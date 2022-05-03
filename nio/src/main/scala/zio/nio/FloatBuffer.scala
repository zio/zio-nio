package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace, UIO, ZIO}

import java.nio.{ByteOrder, FloatBuffer => JFloatBuffer}

/**
 * A mutable buffer of floats.
 */
final class FloatBuffer(protected[nio] val buffer: JFloatBuffer) extends Buffer[Float] {

  override protected[nio] def array(implicit trace: Trace): UIO[Array[Float]] = ZIO.succeed(buffer.array())

  override def order(implicit trace: Trace): UIO[ByteOrder] = ZIO.succeed(buffer.order)

  override def slice(implicit trace: Trace): UIO[FloatBuffer] = ZIO.succeed(new FloatBuffer(buffer.slice()))

  override def compact(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.compact()).unit

  override def duplicate(implicit trace: Trace): UIO[FloatBuffer] =
    ZIO.succeed(new FloatBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java float buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java float buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JFloatBuffer => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] = f(buffer)

  override def get(implicit trace: Trace): UIO[Float] = ZIO.succeed(buffer.get())

  override def get(i: Int)(implicit trace: Trace): UIO[Float] = ZIO.succeed(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue)(implicit trace: Trace): UIO[Chunk[Float]] =
    ZIO.succeed {
      val array = Array.ofDim[Float](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Float)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.put(element)).unit

  override def put(index: Int, element: Float)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Float])(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer(implicit trace: Trace): UIO[FloatBuffer] =
    ZIO.succeed(new FloatBuffer(buffer.asReadOnlyBuffer()))

}
