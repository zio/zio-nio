package zio.nio

import zio.{Chunk, UIO, ZIO}

import java.nio.{ByteOrder, FloatBuffer => JFloatBuffer}

/**
 * A mutable buffer of floats.
 */
final class FloatBuffer(protected[nio] val buffer: JFloatBuffer) extends Buffer[Float] {

  override protected[nio] def array: UIO[Array[Float]] = UIO.succeed(buffer.array())

  override def order: UIO[ByteOrder] = UIO.succeed(buffer.order)

  override def slice: UIO[FloatBuffer] = UIO.succeed(new FloatBuffer(buffer.slice()))

  override def compact: UIO[Unit] = UIO.succeed(buffer.compact()).unit

  override def duplicate: UIO[FloatBuffer] = UIO.succeed(new FloatBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java float buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java float buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JFloatBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(buffer)

  override def get: UIO[Float] = UIO.succeed(buffer.get())

  override def get(i: Int): UIO[Float] = UIO.succeed(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Float]] =
    UIO.succeed {
      val array = Array.ofDim[Float](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Float): UIO[Unit] = UIO.succeed(buffer.put(element)).unit

  override def put(index: Int, element: Float): UIO[Unit] = UIO.succeed(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Float]): UIO[Unit] =
    UIO.succeed {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[FloatBuffer] = UIO.succeed(new FloatBuffer(buffer.asReadOnlyBuffer()))

}
