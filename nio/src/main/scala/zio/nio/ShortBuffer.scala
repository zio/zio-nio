package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, UIO, ZIO, ZTraceElement}

import java.nio.{ByteOrder, ShortBuffer => JShortBuffer}

/**
 * A mutable buffer of shorts.
 */
final class ShortBuffer(protected[nio] val buffer: JShortBuffer) extends Buffer[Short] {

  override protected[nio] def array(implicit trace: ZTraceElement): UIO[Array[Short]] = UIO.succeed(buffer.array())

  override def order(implicit trace: ZTraceElement): UIO[ByteOrder] = UIO.succeed(buffer.order())

  override def slice(implicit trace: ZTraceElement): UIO[ShortBuffer] = UIO.succeed(new ShortBuffer(buffer.slice()))

  override def compact(implicit trace: ZTraceElement): UIO[Unit] = UIO.succeed(buffer.compact()).unit

  override def duplicate(implicit trace: ZTraceElement): UIO[ShortBuffer] =
    UIO.succeed(new ShortBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java short buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java short buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JShortBuffer => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] = f(buffer)

  override def get(implicit trace: ZTraceElement): UIO[Short] = UIO.succeed(buffer.get())

  override def get(i: Int)(implicit trace: ZTraceElement): UIO[Short] = UIO.succeed(buffer.get(i))

  override def getChunk(maxLength: Int)(implicit trace: ZTraceElement): UIO[Chunk[Short]] =
    UIO.succeed {
      val array = Array.ofDim[Short](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Short)(implicit trace: ZTraceElement): UIO[Unit] = UIO.succeed(buffer.put(element)).unit

  override def put(index: Int, element: Short)(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Short])(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.succeed {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer(implicit trace: ZTraceElement): UIO[ShortBuffer] =
    UIO.succeed(new ShortBuffer(buffer.asReadOnlyBuffer()))

}
