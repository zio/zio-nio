package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, UIO, ZIO, ZTraceElement}

import java.nio.{ByteOrder, IntBuffer => JIntBuffer}

/**
 * A mutable buffer of ints.
 */
final class IntBuffer(protected[nio] val buffer: JIntBuffer) extends Buffer[Int] {

  override protected[nio] def array(implicit trace: ZTraceElement): UIO[Array[Int]] = UIO.succeed(buffer.array())

  override def order(implicit trace: ZTraceElement): UIO[ByteOrder] = UIO.succeed(buffer.order)

  override def slice(implicit trace: ZTraceElement): UIO[IntBuffer] = UIO.succeed(new IntBuffer(buffer.slice()))

  override def compact(implicit trace: ZTraceElement): UIO[Unit] = UIO.succeed(buffer.compact()).unit

  override def duplicate(implicit trace: ZTraceElement): UIO[IntBuffer] = UIO.succeed(new IntBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java int buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java int buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JIntBuffer => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] = f(buffer)

  override def get(implicit trace: ZTraceElement): UIO[Int] = UIO.succeed(buffer.get())

  override def get(i: Int)(implicit trace: ZTraceElement): UIO[Int] = UIO.succeed(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue)(implicit trace: ZTraceElement): UIO[Chunk[Int]] =
    UIO.succeed {
      val array = Array.ofDim[Int](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Int)(implicit trace: ZTraceElement): UIO[Unit] = UIO.succeed(buffer.put(element)).unit

  override def put(index: Int, element: Int)(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Int])(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer(implicit trace: ZTraceElement): UIO[IntBuffer] =
    UIO.succeed(new IntBuffer(buffer.asReadOnlyBuffer()))

}
