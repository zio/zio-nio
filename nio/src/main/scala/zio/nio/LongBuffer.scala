package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, UIO, ZIO, ZTraceElement}

import java.nio.{ByteOrder, LongBuffer => JLongBuffer}

/**
 * A mutable buffer of longs.
 */
final class LongBuffer(protected[nio] val buffer: JLongBuffer) extends Buffer[Long] {

  override protected[nio] def array(implicit trace: ZTraceElement): UIO[Array[Long]] = UIO.succeed(buffer.array())

  override def order(implicit trace: ZTraceElement): UIO[ByteOrder] = UIO.succeed(buffer.order)

  override def slice(implicit trace: ZTraceElement): UIO[LongBuffer] = UIO.succeed(new LongBuffer(buffer.slice()))

  override def compact(implicit trace: ZTraceElement): UIO[Unit] = UIO.succeed(buffer.compact()).unit

  override def duplicate(implicit trace: ZTraceElement): UIO[LongBuffer] =
    UIO.succeed(new LongBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java long buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java long buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JLongBuffer => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] = f(buffer)

  override def get(implicit trace: ZTraceElement): UIO[Long] = UIO.succeed(buffer.get())

  override def get(i: Int)(implicit trace: ZTraceElement): UIO[Long] = UIO.succeed(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue)(implicit trace: ZTraceElement): UIO[Chunk[Long]] =
    UIO.succeed {
      val array = Array.ofDim[Long](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Long)(implicit trace: ZTraceElement): UIO[Unit] = UIO.succeed(buffer.put(element)).unit

  override def put(index: Int, element: Long)(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Long])(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer(implicit trace: ZTraceElement): UIO[LongBuffer] =
    UIO.succeed(new LongBuffer(buffer.asReadOnlyBuffer()))

}
