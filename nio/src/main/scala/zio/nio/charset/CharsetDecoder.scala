package zio
package nio
package charset

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.{ZChannel, ZPipeline}

import java.nio.charset.{MalformedInputException, UnmappableCharacterException}
import java.nio.{charset => j}

/**
 * An engine that can transform a sequence of bytes in a specific charset into a sequence of sixteen-bit Unicode
 * characters.
 *
 * '''Important:''' a decoder instance is ''stateful'', as it internally tracks the state of the current decoding
 * operation.
 *   - a decoder instance cannot be used concurrently, it can only decode a single sequence of bytes at a time
 *   - after a decode operation is completed, the `reset` method must be used to reset the decoder before using it again
 *     on a new sequence of bytes
 */
final class CharsetDecoder private (val javaDecoder: j.CharsetDecoder) extends AnyVal {

  def averageCharsPerByte: Float = javaDecoder.averageCharsPerByte()

  def charset: Charset = Charset.fromJava(javaDecoder.charset())

  def decode(in: ByteBuffer)(implicit trace: ZTraceElement): IO[j.CharacterCodingException, CharBuffer] =
    in.withJavaBuffer[Any, Throwable, CharBuffer](jBuf => IO.attempt(Buffer.charFromJava(javaDecoder.decode(jBuf))))
      .refineToOrDie[j.CharacterCodingException]

  def decode(
    in: ByteBuffer,
    out: CharBuffer,
    endOfInput: Boolean
  )(implicit trace: ZTraceElement): UIO[CoderResult] =
    in.withJavaBuffer { jIn =>
      out.withJavaBuffer { jOut =>
        IO.succeed(
          CoderResult.fromJava(javaDecoder.decode(jIn, jOut, endOfInput))
        )
      }
    }

  def autoDetect(implicit trace: ZTraceElement): UIO[AutoDetect] =
    UIO.succeed {
      if (javaDecoder.isAutoDetecting)
        if (javaDecoder.isCharsetDetected)
          AutoDetect.Detected(Charset.fromJava(javaDecoder.detectedCharset()))
        else
          AutoDetect.NotDetected
      else
        AutoDetect.NotSupported
    }

  def flush(out: CharBuffer)(implicit trace: ZTraceElement): UIO[CoderResult] =
    out.withJavaBuffer(jOut => UIO.succeed(CoderResult.fromJava(javaDecoder.flush(jOut))))

  def malformedInputAction(implicit trace: ZTraceElement): UIO[j.CodingErrorAction] =
    UIO.succeed(javaDecoder.malformedInputAction())

  def onMalformedInput(errorAction: j.CodingErrorAction)(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed(javaDecoder.onMalformedInput(errorAction)).unit

  def unmappableCharacterAction(implicit trace: ZTraceElement): UIO[j.CodingErrorAction] =
    UIO.succeed(javaDecoder.unmappableCharacterAction())

  def onUnmappableCharacter(errorAction: j.CodingErrorAction)(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed(javaDecoder.onUnmappableCharacter(errorAction)).unit

  def maxCharsPerByte: Float = javaDecoder.maxCharsPerByte()

  def replacement(implicit trace: ZTraceElement): UIO[String] = UIO.succeed(javaDecoder.replacement())

  def replaceWith(replacement: String)(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed(javaDecoder.replaceWith(replacement)).unit

  /**
   * Resets this decoder, clearing any internal state.
   */
  def reset(implicit trace: ZTraceElement): UIO[Unit] = UIO.succeed(javaDecoder.reset()).unit

  /**
   * Decodes a stream of bytes into characters according to this character set's encoding.
   *
   * Note the returned transducer is tied to this decoder and cannot be used concurrently.
   *
   * @param bufSize
   *   The size of the internal buffer used for encoding. Must be at least 50.
   */
  def transducer(
    bufSize: Int = 5000
  )(implicit trace: ZTraceElement): ZPipeline[Any, j.CharacterCodingException, Byte, Char] = {
    def push(implicit
      trace: ZTraceElement
    ): ZIO[Any, Nothing, Option[Chunk[Byte]] => IO[j.CharacterCodingException, Chunk[Char]]] =
      for {
        _          <- reset
        byteBuffer <- Buffer.byte(bufSize)
        charBuffer <- Buffer.char((bufSize.toFloat * this.averageCharsPerByte).round)
      } yield {

        def handleCoderResult(coderResult: CoderResult)(implicit trace: ZTraceElement) =
          coderResult match {
            case CoderResult.Underflow | CoderResult.Overflow =>
              byteBuffer.compact *>
                charBuffer.flip *>
                charBuffer.getChunk() <*
                charBuffer.clear
            case CoderResult.Malformed(length) =>
              IO.fail(new MalformedInputException(length))
            case CoderResult.Unmappable(length) =>
              IO.fail(new UnmappableCharacterException(length))
          }

        (_: Option[Chunk[Byte]]).map { inChunk =>
          def decodeChunk(inBytes: Chunk[Byte])(implicit
            trace: ZTraceElement
          ): IO[j.CharacterCodingException, Chunk[Char]] =
            for {
              bufRemaining <- byteBuffer.remaining
              (decodeBytes, remainingBytes) = {
                if (inBytes.length > bufRemaining)
                  inBytes.splitAt(bufRemaining)
                else
                  (inBytes, Chunk.empty)
              }
              _ <- byteBuffer.putChunk(decodeBytes)
              _ <- byteBuffer.flip
              result <- decode(
                          byteBuffer,
                          charBuffer,
                          endOfInput = false
                        )
              decodedChars   <- handleCoderResult(result)
              remainderChars <- if (remainingBytes.isEmpty) IO.succeed(Chunk.empty) else decodeChunk(remainingBytes)
            } yield decodedChars ++ remainderChars

          decodeChunk(inChunk)
        }.getOrElse {
          def endOfInput(implicit trace: ZTraceElement): IO[j.CharacterCodingException, Chunk[Char]] =
            for {
              result <- decode(
                          byteBuffer,
                          charBuffer,
                          endOfInput = true
                        )
              decodedChars   <- handleCoderResult(result)
              remainderChars <- if (result == CoderResult.Overflow) endOfInput else IO.succeed(Chunk.empty)
            } yield decodedChars ++ remainderChars
          byteBuffer.flip *> endOfInput.flatMap { decodedChars =>
            def flushRemaining(implicit trace: ZTraceElement): IO[j.CharacterCodingException, Chunk[Char]] =
              for {
                result         <- flush(charBuffer)
                decodedChars   <- handleCoderResult(result)
                remainderChars <- if (result == CoderResult.Overflow) flushRemaining else IO.succeed(Chunk.empty)
              } yield decodedChars ++ remainderChars
            flushRemaining.map(decodedChars ++ _)
          } <* byteBuffer.clear <* charBuffer.clear
        }
      }

    if (bufSize < 50)
      ZPipeline.fromChannel(
        ZChannel.fromZIO(ZIO.die(new IllegalArgumentException(s"Buffer size is $bufSize, must be >= 50")))
      )
    else
      ZPipeline.fromPush(push)
  }

}

object CharsetDecoder {

  def fromJava(javaDecoder: j.CharsetDecoder): CharsetDecoder = new CharsetDecoder(javaDecoder)

}
