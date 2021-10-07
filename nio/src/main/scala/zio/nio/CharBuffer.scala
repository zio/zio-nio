package zio.nio

import zio.{Chunk, UIO, ZIO}

import java.nio.{ByteOrder, CharBuffer => JCharBuffer}

/**
 * A mutable buffer of characters.
 */
final class CharBuffer(protected[nio] val buffer: JCharBuffer) extends Buffer[Char] {

  override protected[nio] def array: UIO[Array[Char]] = UIO.effectTotal(buffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(buffer.order())

  override def slice: UIO[CharBuffer] = UIO.effectTotal(new CharBuffer(buffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(buffer.compact()).unit

  override def duplicate: UIO[CharBuffer] = UIO.effectTotal(new CharBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java character buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java character buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JCharBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(buffer)

  override def get: UIO[Char] = UIO.effectTotal(buffer.get())

  override def get(i: Int): UIO[Char] = UIO.effectTotal(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Char]] =
    UIO.effectTotal {
      val array = Array.ofDim[Char](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  def getString: UIO[String] = UIO.effectTotal(buffer.toString())

  override def put(element: Char): UIO[Unit] = UIO.effectTotal(buffer.put(element)).unit

  override def put(index: Int, element: Char): UIO[Unit] = UIO.effectTotal(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Char]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[CharBuffer] = UIO.effectTotal(new CharBuffer(buffer.asReadOnlyBuffer()))

}
