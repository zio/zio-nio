package zio
package nio
package charset

import zio.stream.{ZChannel, ZPipeline}

import java.nio.charset.{MalformedInputException, UnmappableCharacterException}
import java.nio.{charset => j}

/**
 * An engine that can transform a sequence of sixteen-bit Unicode characters into a sequence of bytes in a specific
 * charset.
 *
 * '''Important:''' an encoder instance is ''stateful'', as it internally tracks the state of the current encoding
 * operation.
 *   - an encoder instance cannot be used concurrently, it can only encode a single sequence of characters at a time
 *   - after an encode operation is completed, the `reset` method must be used to reset the decoder before using it
 *     again on a new sequence of characters
 */
final class CharsetEncoder private (val javaEncoder: j.CharsetEncoder) extends AnyVal {

  def averageBytesPerChar: Float = javaEncoder.averageBytesPerChar()

  def charset: Charset = Charset.fromJava(javaEncoder.charset())

  def encode(in: CharBuffer): IO[j.CharacterCodingException, ByteBuffer] =
    in.withJavaBuffer[Any, Throwable, ByteBuffer](jBuf => IO.attempt(Buffer.byteFromJava(javaEncoder.encode(jBuf))))
      .refineToOrDie[j.CharacterCodingException]

  def encode(in: CharBuffer, out: ByteBuffer, endOfInput: Boolean): UIO[CoderResult] =
    in.withJavaBuffer { jIn =>
      out.withJavaBuffer(jOut => IO.succeed(CoderResult.fromJava(javaEncoder.encode(jIn, jOut, endOfInput))))
    }

  def flush(out: ByteBuffer): UIO[CoderResult] =
    out.withJavaBuffer(jOut => UIO.succeed(CoderResult.fromJava(javaEncoder.flush(jOut))))

  def malformedInputAction: UIO[j.CodingErrorAction] = UIO.succeed(javaEncoder.malformedInputAction())

  def onMalformedInput(errorAction: j.CodingErrorAction): UIO[Unit] =
    UIO.succeed(javaEncoder.onMalformedInput(errorAction)).unit

  def unmappableCharacterAction: UIO[j.CodingErrorAction] = UIO.succeed(javaEncoder.unmappableCharacterAction())

  def onUnmappableCharacter(errorAction: j.CodingErrorAction): UIO[Unit] =
    UIO.succeed(javaEncoder.onUnmappableCharacter(errorAction)).unit

  def maxCharsPerByte: Float = javaEncoder.maxBytesPerChar()

  def replacement: UIO[Chunk[Byte]] = UIO.succeed(Chunk.fromArray(javaEncoder.replacement()))

  def replaceWith(replacement: Chunk[Byte]): UIO[Unit] =
    UIO.succeed(javaEncoder.replaceWith(replacement.toArray)).unit

  /**
   * Resets this decoder, clearing any internal state.
   */
  def reset: UIO[Unit] = UIO.succeed(javaEncoder.reset()).unit

  /**
   * Encodes a stream of characters into bytes according to this character set's encoding.
   *
   * Note the returned transducer is tied to this encoder and cannot be used concurrently.
   *
   * @param bufSize
   *   The size of the internal buffer used for encoding. Must be at least 50.
   */
  def transducer(bufSize: Int = 5000): ZPipeline[Any, j.CharacterCodingException, Char, Byte] = {
    val push: UManaged[Option[Chunk[Char]] => IO[j.CharacterCodingException, Chunk[Byte]]] = {
      for {
        _          <- reset.toManaged
        charBuffer <- Buffer.char((bufSize.toFloat / this.averageBytesPerChar).round).toManaged
        byteBuffer <- Buffer.byte(bufSize).toManaged
      } yield {

        def handleCoderResult(coderResult: CoderResult) =
          coderResult match {
            case CoderResult.Underflow | CoderResult.Overflow =>
              charBuffer.compact *>
                byteBuffer.flip *>
                byteBuffer.getChunk() <*
                byteBuffer.clear
            case CoderResult.Malformed(length) =>
              IO.fail(new MalformedInputException(length))
            case CoderResult.Unmappable(length) =>
              IO.fail(new UnmappableCharacterException(length))
          }

        (_: Option[Chunk[Char]]).map { inChunk =>
          def encodeChunk(inChars: Chunk[Char]): IO[j.CharacterCodingException, Chunk[Byte]] =
            for {
              bufRemaining <- charBuffer.remaining
              (decodeChars, remainingChars) = {
                if (inChars.length > bufRemaining)
                  inChars.splitAt(bufRemaining)
                else
                  (inChars, Chunk.empty)
              }
              _              <- charBuffer.putChunk(decodeChars)
              _              <- charBuffer.flip
              result         <- encode(charBuffer, byteBuffer, endOfInput = false)
              encodedBytes   <- handleCoderResult(result)
              remainderBytes <- if (remainingChars.isEmpty) IO.succeed(Chunk.empty) else encodeChunk(remainingChars)
            } yield encodedBytes ++ remainderBytes

          encodeChunk(inChunk)
        }.getOrElse {
          def endOfInput: IO[j.CharacterCodingException, Chunk[Byte]] =
            for {
              result         <- encode(charBuffer, byteBuffer, endOfInput = true)
              encodedBytes   <- handleCoderResult(result)
              remainderBytes <- if (result == CoderResult.Overflow) endOfInput else IO.succeed(Chunk.empty)
            } yield encodedBytes ++ remainderBytes
          charBuffer.flip *> endOfInput.flatMap { encodedBytes =>
            def flushRemaining: IO[j.CharacterCodingException, Chunk[Byte]] =
              for {
                result         <- flush(byteBuffer)
                encodedBytes   <- handleCoderResult(result)
                remainderBytes <- if (result == CoderResult.Overflow) flushRemaining else IO.succeed(Chunk.empty)
              } yield encodedBytes ++ remainderBytes
            flushRemaining.map(encodedBytes ++ _)
          } <* charBuffer.clear <* byteBuffer.clear
        }
      }
    }

    if (bufSize < 50) {
      ZPipeline.fromChannel(ZChannel.fromZIO(ZIO.dieMessage(s"Buffer size is $bufSize, must be >= 50"))) // TODO hack
    } else
      ZPipeline.fromPush(push)
  }

}

object CharsetEncoder {

  def fromJava(javaEncoder: j.CharsetEncoder): CharsetEncoder = new CharsetEncoder(javaEncoder)

}
