package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace, UIO, ZIO}

import java.nio.{ByteOrder, CharBuffer => JCharBuffer}

/**
 * A mutable buffer of characters.
 */
final class CharBuffer(protected[nio] val buffer: JCharBuffer) extends Buffer[Char] {

  override protected[nio] def array(implicit trace: Trace): UIO[Array[Char]] = ZIO.succeed(buffer.array())

  override def order(implicit trace: Trace): UIO[ByteOrder] = ZIO.succeed(buffer.order())

  override def slice(implicit trace: Trace): UIO[CharBuffer] = ZIO.succeed(new CharBuffer(buffer.slice()))

  override def compact(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.compact()).unit

  override def duplicate(implicit trace: Trace): UIO[CharBuffer] =
    ZIO.succeed(new CharBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java character buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java character buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JCharBuffer => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] = f(buffer)

  override def get(implicit trace: Trace): UIO[Char] = ZIO.succeed(buffer.get())

  override def get(i: Int)(implicit trace: Trace): UIO[Char] = ZIO.succeed(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue)(implicit trace: Trace): UIO[Chunk[Char]] =
    ZIO.succeed {
      val array = Array.ofDim[Char](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  def getString(implicit trace: Trace): UIO[String] = ZIO.succeed(buffer.toString())

  override def put(element: Char)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.put(element)).unit

  override def put(index: Int, element: Char)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Char])(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer(implicit trace: Trace): UIO[CharBuffer] =
    ZIO.succeed(new CharBuffer(buffer.asReadOnlyBuffer()))

}
