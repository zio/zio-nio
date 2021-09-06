package zio.nio

import zio.{ Chunk, UIO, ZIO }

import java.nio.{ ByteOrder, LongBuffer => JLongBuffer }

/**
 * A mutable buffer of longs.
 */
final class LongBuffer(protected[nio] val buffer: JLongBuffer) extends Buffer[Long] {

  override protected[nio] def array: UIO[Array[Long]] = UIO.effectTotal(buffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(buffer.order)

  override def slice: UIO[LongBuffer] = UIO.effectTotal(new LongBuffer(buffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(buffer.compact()).unit

  override def duplicate: UIO[LongBuffer] = UIO.effectTotal(new LongBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java long buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java long buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JLongBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(buffer)

  override def get: UIO[Long] = UIO.effectTotal(buffer.get())

  override def get(i: Int): UIO[Long] = UIO.effectTotal(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Long]] =
    UIO.effectTotal {
      val array = Array.ofDim[Long](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Long): UIO[Unit] = UIO.effectTotal(buffer.put(element)).unit

  override def put(index: Int, element: Long): UIO[Unit] = UIO.effectTotal(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Long]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[LongBuffer] = UIO.effectTotal(new LongBuffer(buffer.asReadOnlyBuffer()))

}
