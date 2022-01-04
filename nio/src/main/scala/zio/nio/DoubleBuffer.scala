package zio.nio

import zio.{Chunk, UIO, ZIO}

import java.nio.{ByteOrder, DoubleBuffer => JDoubleBuffer}

/**
 * A mutable buffer of doubles.
 */
final class DoubleBuffer(protected[nio] val buffer: JDoubleBuffer) extends Buffer[Double] {

  override protected[nio] def array: UIO[Array[Double]] = UIO.effectTotal(buffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(buffer.order)

  override def slice: UIO[DoubleBuffer] = UIO.effectTotal(new DoubleBuffer(buffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(buffer.compact()).unit

  override def duplicate: UIO[DoubleBuffer] = UIO.effectTotal(new DoubleBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java double buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java double buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JDoubleBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(buffer)

  override def get: UIO[Double] = UIO.effectTotal(buffer.get())

  override def get(i: Int): UIO[Double] = UIO.effectTotal(buffer.get(i))

  override def getChunk(
    maxLength: Int = Int.MaxValue
  ): UIO[Chunk[Double]] =
    UIO.effectTotal {
      val array = Array.ofDim[Double](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Double): UIO[Unit] = UIO.effectTotal(buffer.put(element)).unit

  override def put(index: Int, element: Double): UIO[Unit] = UIO.effectTotal(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Double]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[DoubleBuffer] = UIO.effectTotal(new DoubleBuffer(buffer.asReadOnlyBuffer()))

}
