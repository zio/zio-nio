package zio
package nio
package core
package charset

import java.nio.charset.{ MalformedInputException, UnmappableCharacterException }
import java.nio.{ charset => j }

import zio.stream.ZStream
import zio.stream.ZStream.Pull

final class CharsetDecoder private (val javaDecoder: j.CharsetDecoder) extends AnyVal {

  def averageCharsPerByte: Float = javaDecoder.averageCharsPerByte()

  def charset: Charset = Charset.fromJava(javaDecoder.charset())

  def decode(in: ByteBuffer): IO[j.CharacterCodingException, CharBuffer] =
    in.withJavaBuffer[Any, Throwable, CharBuffer](jBuf => IO.effect(Buffer.charFromJava(javaDecoder.decode(jBuf))))
      .refineToOrDie[j.CharacterCodingException]

  def decode(
    in: ByteBuffer,
    out: CharBuffer,
    endOfInput: Boolean
  ): UIO[CoderResult] =
    in.withJavaBuffer { jIn =>
      out.withJavaBuffer { jOut =>
        IO.effectTotal(
          CoderResult.fromJava(javaDecoder.decode(jIn, jOut, endOfInput))
        )
      }
    }

  def autoDetect: UIO[AutoDetect] = UIO.effectTotal {
    if (javaDecoder.isAutoDetecting()) {
      if (javaDecoder.isCharsetDetected()) {
        AutoDetect.Detected(Charset.fromJava(javaDecoder.detectedCharset()))
      } else {
        AutoDetect.NotDetected
      }
    } else {
      AutoDetect.NotSupported
    }
  }

  def flush(out: CharBuffer): UIO[CoderResult] = out.withJavaBuffer { jOut =>
    UIO.effectTotal(CoderResult.fromJava(javaDecoder.flush(jOut)))
  }

  def malformedInputAction: UIO[j.CodingErrorAction] =
    UIO.effectTotal(javaDecoder.malformedInputAction())

  def onMalformedInput(errorAction: j.CodingErrorAction): UIO[Unit] =
    UIO.effectTotal(javaDecoder.onMalformedInput(errorAction)).unit

  def unmappableCharacterAction: UIO[j.CodingErrorAction] =
    UIO.effectTotal(javaDecoder.unmappableCharacterAction())

  def onUnmappableCharacter(errorAction: j.CodingErrorAction): UIO[Unit] =
    UIO.effectTotal(javaDecoder.onUnmappableCharacter(errorAction)).unit

  def maxCharsPerByte: Float = javaDecoder.maxCharsPerByte()

  def replacement: UIO[String] = UIO.effectTotal(javaDecoder.replacement())

  def replaceWith(replacement: String): UIO[Unit] =
    UIO.effectTotal(javaDecoder.replaceWith(replacement)).unit

  def reset: UIO[Unit] = UIO.effectTotal(javaDecoder.reset()).unit

  def decodeStream[R](
    stream: ZStream[R, Nothing, Chunk[Byte]],
    bufSize: Int = 5000
  ): ZStream[R, j.CharacterCodingException, Chunk[Char]] =
    decodeStreamError(stream, bufSize)(identity)

  /**
   * Applies character decoding to a stream of byte chunks.
   *
   * @param stream  The input stream of byte chunks.
   * @param bufSize The size of the internal buffer used for encoding.
   *                Must be at least 50.
   * @param handleError Used to report decoding failures to the stream consumer.
   */
  def decodeStreamError[R, E](
    stream: ZStream[R, E, Chunk[Byte]],
    bufSize: Int = 5000
  )(handleError: j.CharacterCodingException => E): ZStream[R, E, Chunk[Char]] = {
    val pull: ZManaged[R, Nothing, Pull[R, E, Chunk[Char]]] =
      for {
        byteBuffer <- Buffer.byte(bufSize).toManaged_.orDie
        charBuffer <- Buffer.char((bufSize.toFloat * this.averageCharsPerByte).round).toManaged_.orDie
        inPull     <- stream.process
        stateRef   <- Ref.make[StreamCodeState](StreamCodeState.Pull).toManaged_
      } yield {
        def handleCoderResult(coderResult: CoderResult) = coderResult match {
          case CoderResult.Underflow | CoderResult.Overflow =>
            byteBuffer.compact.orDie *>
              charBuffer.flip *>
              charBuffer.getChunk().orDie <*
              charBuffer.clear
          case CoderResult.Malformed(length) =>
            ZIO.fail(Some(handleError(new MalformedInputException(length))))
          case CoderResult.Unmappable(length) =>
            ZIO.fail(Some(handleError(new UnmappableCharacterException(length))))
        }

        stateRef.get.flatMap {
          case StreamCodeState.Pull =>
            def decode(
              inBytes: Chunk[Byte]
            ): ZIO[Any, Some[E], Chunk[Char]] =
              for {
                bufRemaining <- byteBuffer.remaining
                (decodeBytes, remainingBytes) = {
                  if (inBytes.length > bufRemaining) {
                    inBytes.splitAt(bufRemaining)
                  } else {
                    (inBytes, Chunk.empty)
                  }
                }
                _ <- byteBuffer.putChunk(decodeBytes).orDie
                _ <- byteBuffer.flip
                result <- this.decode(
                           byteBuffer,
                           charBuffer,
                           endOfInput = false
                         )
                decodedChars   <- handleCoderResult(result)
                remainderChars <- if (remainingBytes.isEmpty) ZIO.succeed(Chunk.empty) else decode(remainingBytes)
              } yield decodedChars ++ remainderChars

            inPull.foldM(
              _.map(e => ZIO.fail(Some(e))).getOrElse {
                stateRef.set(StreamCodeState.EndOfInput).as(Chunk.empty)
              },
              decode
            )
          case StreamCodeState.EndOfInput =>
            for {
              _ <- byteBuffer.flip
              result <- this.decode(
                         byteBuffer,
                         charBuffer,
                         endOfInput = true
                       )
              outChars <- handleCoderResult(result)
              _ <- ZIO.when(result == CoderResult.Underflow)(
                    stateRef.set(StreamCodeState.Flush)
                  )
            } yield outChars
          case StreamCodeState.Flush =>
            for {
              result <- this.flush(charBuffer)
              outChars <- result match {
                           case CoderResult.Underflow =>
                             charBuffer.flip *> charBuffer.getChunk().orDie <* stateRef.set(
                               StreamCodeState.Done
                             )
                           case CoderResult.Overflow =>
                             charBuffer.flip *> charBuffer.getChunk().orDie <* charBuffer.clear
                           case e =>
                             ZIO.dieMessage(
                               s"Error $e should not returned from decoder flush"
                             )
                         }
            } yield outChars
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

object CharsetDecoder {

  def fromJava(javaDecoder: j.CharsetDecoder): CharsetDecoder =
    new CharsetDecoder(javaDecoder)

}
