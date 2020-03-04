package zio
package nio
package core
package charset

import java.nio.charset.{ MalformedInputException, UnmappableCharacterException }
import java.nio.{ charset => j }

import zio.stream.ZStream
import zio.stream.ZStream.Pull

final class CharsetEncoder private (val javaEncoder: j.CharsetEncoder) extends AnyVal {

  def averageBytesPerChar: Float = javaEncoder.averageBytesPerChar()

  def charset: Charset = Charset.fromJava(javaEncoder.charset())

  def encode(in: CharBuffer): IO[j.CharacterCodingException, ByteBuffer] =
    in.withJavaBuffer(jBuf =>
      IO.effect(Buffer.byteFromJava(javaEncoder.encode(jBuf)))
        .refineToOrDie[j.CharacterCodingException]
    )

  def encode(in: CharBuffer, out: ByteBuffer, endOfInput: Boolean): UIO[CoderResult] =
    in.withJavaBuffer { jIn =>
      out.withJavaBuffer(jOut => IO.effectTotal(CoderResult.fromJava(javaEncoder.encode(jIn, jOut, endOfInput))))
    }

  def flush(out: ByteBuffer): UIO[CoderResult] = out.withJavaBuffer { jOut =>
    UIO.effectTotal(CoderResult.fromJava(javaEncoder.flush(jOut)))
  }

  def malformedInputAction: UIO[j.CodingErrorAction] =
    UIO.effectTotal(javaEncoder.malformedInputAction())

  def onMalformedInput(errorAction: j.CodingErrorAction): UIO[Unit] =
    UIO.effectTotal(javaEncoder.onMalformedInput(errorAction)).unit

  def unmappableCharacterAction: UIO[j.CodingErrorAction] =
    UIO.effectTotal(javaEncoder.unmappableCharacterAction())

  def onUnmappableCharacter(errorAction: j.CodingErrorAction): UIO[Unit] =
    UIO.effectTotal(javaEncoder.onUnmappableCharacter(errorAction)).unit

  def maxCharsPerByte: Float = javaEncoder.maxBytesPerChar()

  def replacement: UIO[Chunk[Byte]] = UIO.effectTotal(Chunk.fromArray(javaEncoder.replacement()))

  def replaceWith(replacement: Chunk[Byte]): UIO[Unit] =
    UIO.effectTotal(javaEncoder.replaceWith(replacement.toArray)).unit

  def reset: UIO[Unit] = UIO.effectTotal(javaEncoder.reset()).unit

  /**
   * Applies character encoding to a stream of character chunks.
   * This is a simplified variant of `encodeStreamError` that can be used for streams
   * that can't fail.
   *
   * @param stream      The input stream of character chunks.
   * @param bufSize     The size of the internal buffer used for encoding.
   *                    Must be at least 50.
   */
  def encodeStream[R](
    stream: ZStream[R, Nothing, Chunk[Char]],
    bufSize: Int = 5000
  ): ZStream[R, j.CharacterCodingException, Chunk[Byte]] =
    encodeStreamError(stream, bufSize)(identity)

  /**
   * Applies character encoding to a stream of character chunks.
   *
   * @param stream The input stream of character chunks.
   * @param bufSize The size of the internal buffer used for encoding.
   *                Must be at least 50.
   * @param handleError Used to report encoding failures to the stream consumer.
   */
  def encodeStreamError[R, E](
    stream: ZStream[R, E, Chunk[Char]],
    bufSize: Int = 5000
  )(handleError: j.CharacterCodingException => E): ZStream[R, E, Chunk[Byte]] = {
    val pull: ZManaged[R, Nothing, Pull[R, E, Chunk[Byte]]] =
      for {
        charBuffer <- Buffer.char((bufSize.toFloat / this.averageBytesPerChar).round).toManaged_.orDie
        byteBuffer <- Buffer.byte(bufSize).toManaged_.orDie
        inPull     <- stream.process
        stateRef   <- Ref.make[StreamCodeState](StreamCodeState.Pull).toManaged_
      } yield {
        def handleCoderResult(coderResult: CoderResult) = coderResult match {
          case CoderResult.Underflow | CoderResult.Overflow =>
            charBuffer.compact.orDie *>
              byteBuffer.flip *>
              byteBuffer.getChunk().orDie <*
              byteBuffer.clear
          case CoderResult.Malformed(length) =>
            ZIO.fail(Some(handleError(new MalformedInputException(length))))
          case CoderResult.Unmappable(length) =>
            ZIO.fail(Some(handleError(new UnmappableCharacterException(length))))
        }

        stateRef.get.flatMap {
          case StreamCodeState.Pull =>
            def encode(inChars: Chunk[Char]): ZIO[Any, Some[E], Chunk[Byte]] =
              for {
                bufRemaining <- byteBuffer.remaining
                (encodeChars, remainingChars) = {
                  if (inChars.length > bufRemaining) {
                    inChars.splitAt(bufRemaining)
                  } else {
                    (inChars, Chunk.empty)
                  }
                }
                _              <- charBuffer.putChunk(encodeChars).orDie
                _              <- charBuffer.flip
                result         <- this.encode(charBuffer, byteBuffer, endOfInput = false)
                encodedBytes   <- handleCoderResult(result)
                remainderBytes <- if (remainingChars.isEmpty) ZIO.succeed(Chunk.empty) else encode(remainingChars)
              } yield encodedBytes ++ remainderBytes

            inPull.foldM(
              _.map(e => ZIO.fail(Some(e))).getOrElse {
                stateRef.set(StreamCodeState.EndOfInput).as(Chunk.empty)
              },
              encode
            )
          case StreamCodeState.EndOfInput =>
            for {
              _ <- charBuffer.flip
              result <- this.encode(
                         charBuffer,
                         byteBuffer,
                         endOfInput = true
                       )
              outBytes <- handleCoderResult(result)
              _        <- ZIO.when(result == CoderResult.Underflow)(stateRef.set(StreamCodeState.Flush))
            } yield outBytes
          case StreamCodeState.Flush =>
            for {
              result <- this.flush(byteBuffer)
              outBytes <- result match {
                           case CoderResult.Underflow =>
                             byteBuffer.flip *>
                               byteBuffer.getChunk().orDie <*
                               stateRef.set(StreamCodeState.Done)
                           case CoderResult.Overflow =>
                             byteBuffer.flip *>
                               byteBuffer.getChunk().orDie <*
                               charBuffer.clear
                           case e =>
                             ZIO.dieMessage(
                               s"Error $e should not returned from encoder flush"
                             )
                         }
            } yield outBytes
          case StreamCodeState.Done =>
            IO.fail(None)
        }
      }

    if (bufSize < 50)
      ZStream.die(new IllegalArgumentException(s"Buffer size is $bufSize, must be >= 50"))
    else
      ZStream(pull)
  }
}

object CharsetEncoder {

  def fromJava(javaEncoder: j.CharsetEncoder): CharsetEncoder =
    new CharsetEncoder(javaEncoder)

}
