package zio.nio.core

import java.nio.{ ByteOrder, ShortBuffer => JShortBuffer }

import zio.{ Chunk, UIO, ZIO }

/**
 * A mutable buffer of shorts.
 */
final class ShortBuffer(shortBuffer: JShortBuffer) extends Buffer[Short](shortBuffer) {

  override protected[nio] def array: UIO[Array[Short]] = UIO.effectTotal(shortBuffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(shortBuffer.order())

  override def slice: UIO[ShortBuffer] = UIO.effectTotal(new ShortBuffer(shortBuffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(shortBuffer.compact()).unit

  override def duplicate: UIO[ShortBuffer] = UIO.effectTotal(new ShortBuffer(shortBuffer.duplicate()))

  /**
   * Provides the underlying Java short buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java short buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JShortBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(shortBuffer)

  override def get: UIO[Short] = UIO.effectTotal(shortBuffer.get())

  override def get(i: Int): UIO[Short] = UIO.effectTotal(shortBuffer.get(i))

  override def getChunk(maxLength: Int): UIO[Chunk[Short]] =
    UIO.effectTotal {
      val array = Array.ofDim[Short](math.min(maxLength, shortBuffer.remaining()))
      shortBuffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Short): UIO[Unit] = UIO.effectTotal(shortBuffer.put(element)).unit

  override def put(index: Int, element: Short): UIO[Unit] = UIO.effectTotal(shortBuffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Short]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      shortBuffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[ShortBuffer] = UIO.effectTotal(new ShortBuffer(shortBuffer.asReadOnlyBuffer()))

}
