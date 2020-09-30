package zio.nio.core

import java.nio.{ MappedByteBuffer => JMappedByteBuffer }

import zio.{ IO, UIO, ZIO }
import zio.blocking.Blocking

/**
 * A direct byte buffer whose content is a memory-mapped region of a file.
 * Mapped byte buffers are created by the [[zio.nio.core.channels.FileChannel.map]] method.
 */
final class MappedByteBuffer private[nio] (javaBuffer: JMappedByteBuffer) extends ByteBuffer(javaBuffer) {

  /**
   * Tells whether or not this buffer's content is resident in physical memory.
   */
  def isLoaded: UIO[Boolean] = IO.effectTotal(javaBuffer.isLoaded)

  /**
   * Loads this buffer's content into physical memory.
   */
  def load: ZIO[Blocking, Nothing, Unit] = ZIO.accessM(_.get.blocking(IO.effectTotal(javaBuffer.load()).unit))

  /**
   * Forces any changes made to this buffer's content to be written to the storage device containing the mapped file.
   */
  def force: ZIO[Blocking, Nothing, Unit] = ZIO.accessM(_.get.blocking(IO.effectTotal(javaBuffer.force()).unit))
}
