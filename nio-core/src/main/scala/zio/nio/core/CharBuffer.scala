package zio.nio.core

import java.nio.{ ByteOrder, CharBuffer => JCharBuffer }

import zio.{ Chunk, UIO, ZIO }

/**
 * A mutable buffer of characters.
 */
final class CharBuffer(charBuffer: JCharBuffer) extends Buffer[Char](charBuffer) {

  override protected[nio] def array: UIO[Array[Char]] = UIO.effectTotal(charBuffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(charBuffer.order())

  override def slice: UIO[CharBuffer] = UIO.effectTotal(new CharBuffer(charBuffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(charBuffer.compact()).unit

  override def duplicate: UIO[CharBuffer] = UIO.effectTotal(new CharBuffer(charBuffer.duplicate()))

  /**
   * Provides the underlying Java character buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java character buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JCharBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(charBuffer)

  override def get: UIO[Char] = UIO.effectTotal(charBuffer.get())

  override def get(i: Int): UIO[Char] = UIO.effectTotal(charBuffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Char]] =
    UIO.effectTotal {
      val array = Array.ofDim[Char](math.min(maxLength, charBuffer.remaining()))
      charBuffer.get(array)
      Chunk.fromArray(array)
    }

  def getString: UIO[String] = UIO.effectTotal(charBuffer.toString())

  override def put(element: Char): UIO[Unit] = UIO.effectTotal(charBuffer.put(element)).unit

  override def put(index: Int, element: Char): UIO[Unit] = UIO.effectTotal(charBuffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Char]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      charBuffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[CharBuffer] = UIO.effectTotal(new CharBuffer(charBuffer.asReadOnlyBuffer()))

}
