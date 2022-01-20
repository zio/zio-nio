package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, UIO, ZIO, ZTraceElement}

import java.nio.{MappedByteBuffer => JMappedByteBuffer}

/**
 * A direct byte buffer whose content is a memory-mapped region of a file. Mapped byte buffers are created by the
 * `FileChannel#map` method.
 *
 * @see
 *   zio.nio.channels.FileChannel#map
 */
final class MappedByteBuffer private[nio] (override protected[nio] val buffer: JMappedByteBuffer)
    extends ByteBuffer(buffer) {

  /**
   * Tells whether or not this buffer's content is resident in physical memory.
   */
  def isLoaded(implicit trace: ZTraceElement): UIO[Boolean] = IO.succeed(buffer.isLoaded)

  /**
   * Loads this buffer's content into physical memory.
   */
  def load(implicit trace: ZTraceElement): ZIO[Any, Nothing, Unit] = ZIO.blocking(IO.succeed(buffer.load()).unit)

  /**
   * Forces any changes made to this buffer's content to be written to the storage device containing the mapped file.
   */
  def force(implicit trace: ZTraceElement): ZIO[Any, Nothing, Unit] = ZIO.blocking(IO.succeed(buffer.force()).unit)
}
