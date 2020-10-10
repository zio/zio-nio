package zio.nio

import java.nio.{ ByteOrder, FloatBuffer => JFloatBuffer }

import zio.{ Chunk, UIO, ZIO }

/**
 * A mutable buffer of floats.
 */
final class FloatBuffer(protected[nio] val buffer: JFloatBuffer) extends Buffer[Float] {

  override protected[nio] def array: UIO[Array[Float]] = UIO.effectTotal(buffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(buffer.order)

  override def slice: UIO[FloatBuffer] = UIO.effectTotal(new FloatBuffer(buffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(buffer.compact()).unit

  override def duplicate: UIO[FloatBuffer] = UIO.effectTotal(new FloatBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java float buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java float buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JFloatBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(buffer)

  override def get: UIO[Float] = UIO.effectTotal(buffer.get())

  override def get(i: Int): UIO[Float] = UIO.effectTotal(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Float]] =
    UIO.effectTotal {
      val array = Array.ofDim[Float](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Float): UIO[Unit] = UIO.effectTotal(buffer.put(element)).unit

  override def put(index: Int, element: Float): UIO[Unit] = UIO.effectTotal(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Float]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[FloatBuffer] = UIO.effectTotal(new FloatBuffer(buffer.asReadOnlyBuffer()))

}
