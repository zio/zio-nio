package zio.nio

import java.nio.{ ByteOrder, LongBuffer => JLongBuffer }

import zio.{ Chunk, UIO, ZIO }

/**
 * A mutable buffer of longs.
 */
final class LongBuffer(longBuffer: JLongBuffer) extends Buffer[Long](longBuffer) {

  override protected[nio] def array: UIO[Array[Long]] =
    UIO.effectTotal(longBuffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(longBuffer.order)

  override def slice: UIO[LongBuffer] =
    UIO.effectTotal(new LongBuffer(longBuffer.slice()))

  override def compact: UIO[Unit] =
    UIO.effectTotal(longBuffer.compact()).unit

  override def duplicate: UIO[LongBuffer] =
    UIO.effectTotal(new LongBuffer(longBuffer.duplicate()))

  /**
   * Provides the underlying Java long buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java long buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JLongBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(longBuffer)

  override def get: UIO[Long] =
    UIO.effectTotal(longBuffer.get())

  override def get(i: Int): UIO[Long] =
    UIO.effectTotal(longBuffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Long]] =
    UIO.effectTotal {
      val array = Array.ofDim[Long](math.min(maxLength, longBuffer.remaining()))
      longBuffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Long): UIO[Unit] =
    UIO.effectTotal(longBuffer.put(element)).unit

  override def put(index: Int, element: Long): UIO[Unit] =
    UIO.effectTotal(longBuffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Long]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      longBuffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[LongBuffer] =
    UIO.effectTotal(new LongBuffer(longBuffer.asReadOnlyBuffer()))

}
