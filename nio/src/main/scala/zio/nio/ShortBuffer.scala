package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace, UIO, ZIO}

import java.nio.{ByteOrder, ShortBuffer => JShortBuffer}

/**
 * A mutable buffer of shorts.
 */
final class ShortBuffer(protected[nio] val buffer: JShortBuffer) extends Buffer[Short] {

  override protected[nio] def array(implicit trace: Trace): UIO[Array[Short]] = ZIO.succeed(buffer.array())

  override def order(implicit trace: Trace): UIO[ByteOrder] = ZIO.succeed(buffer.order())

  override def slice(implicit trace: Trace): UIO[ShortBuffer] = ZIO.succeed(new ShortBuffer(buffer.slice()))

  override def compact(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.compact()).unit

  override def duplicate(implicit trace: Trace): UIO[ShortBuffer] =
    ZIO.succeed(new ShortBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java short buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java short buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JShortBuffer => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] = f(buffer)

  override def get(implicit trace: Trace): UIO[Short] = ZIO.succeed(buffer.get())

  override def get(i: Int)(implicit trace: Trace): UIO[Short] = ZIO.succeed(buffer.get(i))

  override def getChunk(maxLength: Int)(implicit trace: Trace): UIO[Chunk[Short]] =
    ZIO.succeed {
      val array = Array.ofDim[Short](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Short)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.put(element)).unit

  override def put(index: Int, element: Short)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Short])(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer(implicit trace: Trace): UIO[ShortBuffer] =
    ZIO.succeed(new ShortBuffer(buffer.asReadOnlyBuffer()))

}
