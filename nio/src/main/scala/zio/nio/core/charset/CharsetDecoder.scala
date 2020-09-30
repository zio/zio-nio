package zio
package nio
package core
package charset

import java.nio.{ charset => j }
import java.nio.charset.{ MalformedInputException, UnmappableCharacterException }

import zio.stream.{ Transducer, ZTransducer }

/**
 * An engine that can transform a sequence of bytes in a specific charset into a sequence of sixteen-bit Unicode characters.
 *
 * '''Important:''' a decoder instance is ''stateful'', as it internally tracks the state of the current decoding operation.
 *   - a decoder instance cannot be used concurrently, it can only decode a single sequence of bytes at a time
 *   - after a decode operation is completed, the `reset` method must be used to reset the decoder before using it again
 *     on a new sequence of bytes
 */
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

  def autoDetect: UIO[AutoDetect] =
    UIO.effectTotal {
      if (javaDecoder.isAutoDetecting)
        if (javaDecoder.isCharsetDetected)
          AutoDetect.Detected(Charset.fromJava(javaDecoder.detectedCharset()))
        else
          AutoDetect.NotDetected
      else
        AutoDetect.NotSupported
    }

  def flush(out: CharBuffer): UIO[CoderResult] =
    out.withJavaBuffer(jOut => UIO.effectTotal(CoderResult.fromJava(javaDecoder.flush(jOut))))

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

  /**
   * Resets this decoder, clearing any internal state.
   */
  def reset: UIO[Unit] = UIO.effectTotal(javaDecoder.reset()).unit

  /**
   * Decodes a stream of bytes into characters according to this character set's encoding.
   *
   * Note the returned transducer is tied to this decoder and cannot be used concurrently.
   *
   * @param bufSize The size of the internal buffer used for encoding.
   *                Must be at least 50.
   */
  def transducer(bufSize: Int = 5000): Transducer[j.CharacterCodingException, Byte, Char] = {
    val push: Managed[Nothing, Option[Chunk[Byte]] => IO[j.CharacterCodingException, Chunk[Char]]] = {
      for {
        _          <- reset.toManaged_
        byteBuffer <- Buffer.byte(bufSize).toManaged_
        charBuffer <- Buffer.char((bufSize.toFloat * this.averageCharsPerByte).round).toManaged_
      } yield {

        def handleCoderResult(coderResult: CoderResult) =
          coderResult match {
            case CoderResult.Underflow | CoderResult.Overflow =>
              byteBuffer.compact *>
                charBuffer.flip *>
                charBuffer.getChunk() <*
                charBuffer.clear
            case CoderResult.Malformed(length)                =>
              IO.fail(new MalformedInputException(length))
            case CoderResult.Unmappable(length)               =>
              IO.fail(new UnmappableCharacterException(length))
          }

        (_: Option[Chunk[Byte]])
          .map { inChunk =>
            def decodeChunk(inBytes: Chunk[Byte]): IO[j.CharacterCodingException, Chunk[Char]] =
              for {
                bufRemaining                 <- byteBuffer.remaining
                (decodeBytes, remainingBytes) = {
                  if (inBytes.length > bufRemaining)
                    inBytes.splitAt(bufRemaining)
                  else
                    (inBytes, Chunk.empty)
                }
                _                            <- byteBuffer.putChunk(decodeBytes)
                _                            <- byteBuffer.flip
                result                       <- decode(
                                                  byteBuffer,
                                                  charBuffer,
                                                  endOfInput = false
                                                )
                decodedChars                 <- handleCoderResult(result)
                remainderChars               <- if (remainingBytes.isEmpty) IO.succeed(Chunk.empty) else decodeChunk(remainingBytes)
              } yield decodedChars ++ remainderChars

            decodeChunk(inChunk)
          }
          .getOrElse {
            def endOfInput: IO[j.CharacterCodingException, Chunk[Char]] =
              for {
                result         <- decode(
                                    byteBuffer,
                                    charBuffer,
                                    endOfInput = true
                                  )
                decodedChars   <- handleCoderResult(result)
                remainderChars <- if (result == CoderResult.Overflow) endOfInput else IO.succeed(Chunk.empty)
              } yield decodedChars ++ remainderChars
            byteBuffer.flip *> endOfInput.flatMap { decodedChars =>
              def flushRemaining: IO[j.CharacterCodingException, Chunk[Char]] =
                for {
                  result         <- flush(charBuffer)
                  decodedChars   <- handleCoderResult(result)
                  remainderChars <- if (result == CoderResult.Overflow) flushRemaining else IO.succeed(Chunk.empty)
                } yield decodedChars ++ remainderChars
              flushRemaining.map(decodedChars ++ _)
            } <* byteBuffer.clear <* charBuffer.clear
          }
      }
    }

    if (bufSize < 50)
      ZTransducer.die(new IllegalArgumentException(s"Buffer size is $bufSize, must be >= 50"))
    else
      ZTransducer(push)
  }

}

object CharsetDecoder {

  def fromJava(javaDecoder: j.CharsetDecoder): CharsetDecoder =
    new CharsetDecoder(javaDecoder)

}
