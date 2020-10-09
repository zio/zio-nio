package zio.nio

import java.nio.{ MappedByteBuffer => JMappedByteBuffer }

import zio.{ IO, UIO, ZIO }
import zio.blocking.Blocking

/**
 * A direct byte buffer whose content is a memory-mapped region of a file.
 * Mapped byte buffers are created by the `FileChannel#map` method.
 *
 * @see zio.nio.channels.FileChannel#map
 */
final class MappedByteBuffer private[nio] (override protected[nio] val buffer: JMappedByteBuffer)
    extends ByteBuffer(buffer) {

  /**
   * Tells whether or not this buffer's content is resident in physical memory.
   */
  def isLoaded: UIO[Boolean] = IO.effectTotal(buffer.isLoaded)

  /**
   * Loads this buffer's content into physical memory.
   */
  def load: ZIO[Blocking, Nothing, Unit] = ZIO.accessM(_.get.blocking(IO.effectTotal(buffer.load()).unit))

  /**
   * Forces any changes made to this buffer's content to be written to the storage device containing the mapped file.
   */
  def force: ZIO[Blocking, Nothing, Unit] = ZIO.accessM(_.get.blocking(IO.effectTotal(buffer.force()).unit))
}
