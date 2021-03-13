package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ FileChannel => JFileChannel }
import java.nio.file.OpenOption
import java.nio.file.attribute.FileAttribute

import com.github.ghik.silencer.silent
import zio.blocking.Blocking
import zio.nio.core.file.Path
import zio.nio.core.{ ByteBuffer, IOCloseableManagement, MappedByteBuffer }
import zio.{ IO, Managed, ZIO }

import scala.collection.JavaConverters._

/**
 * A channel for reading, writing, mapping, and manipulating a file.
 *
 * Unlike network channels, file channels are ''seekable'' with a current position that can be changed.
 * Read and write calls operate at the current position.
 */
final class FileChannel private[channels] (protected val channel: JFileChannel) extends BlockingChannel {

  self =>

  type BlockingOps = BlockingFileOps

  final class BlockingFileOps private[FileChannel] () extends GatheringByteOps with ScatteringByteOps {

    override protected[channels] def channel: JFileChannel = self.channel

    def truncate(size: Long): IO[IOException, Unit] = IO.effect(channel.truncate(size)).unit.refineToOrDie[IOException]

    /**
     * Forces any updates to this channel's file to be written to the storage device that contains it.
     *
     * @param metadata If true then this method is required to force changes to both the file's content and metadata to
     *                 be written to storage; otherwise, it need only force content changes to be written
     */
    def force(metadata: Boolean): IO[IOException, Unit] = IO.effect(channel.force(metadata)).refineToOrDie[IOException]

    def transferTo(position: Long, count: Long, target: GatheringByteOps): IO[IOException, Long] =
      IO.effect(channel.transferTo(position, count, target.channel)).refineToOrDie[IOException]

    def transferFrom(src: ScatteringByteOps, position: Long, count: Long): IO[IOException, Long] =
      IO.effect(channel.transferFrom(src.channel, position, count)).refineToOrDie[IOException]

    def read(dst: ByteBuffer, position: Long): IO[IOException, Int] =
      dst
        .withJavaBuffer(buffer => IO.effect(channel.read(buffer, position)))
        .refineToOrDie[IOException]

    def write(src: ByteBuffer, position: Long): IO[IOException, Int] =
      src
        .withJavaBuffer(buffer => IO.effect(channel.write(buffer, position)))
        .refineToOrDie[IOException]

    def map(mode: JFileChannel.MapMode, position: Long, size: Long): IO[IOException, MappedByteBuffer] =
      IO.effect(new MappedByteBuffer(channel.map(mode, position, size)))
        .refineToOrDie[IOException]

    def lock(
      position: Long = 0L,
      size: Long = Long.MaxValue,
      shared: Boolean = false
    ): IO[IOException, FileLock] =
      IO.effect(new FileLock(channel.lock(position, size, shared))).refineToOrDie[IOException]

  }

  override def useBlocking[R, E, A](f: BlockingFileOps => ZIO[R, E, A]): ZIO[R with Blocking, E, A] =
    nioBlocking(f(new BlockingOps))

  def position: IO[IOException, Long] = IO.effect(channel.position()).refineToOrDie[IOException]

  def position(newPosition: Long): IO[IOException, Unit] =
    IO.effect(channel.position(newPosition)).unit.refineToOrDie[IOException]

  def size: IO[IOException, Long] = IO.effect(channel.size()).refineToOrDie[IOException]

  def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[IOException, Option[FileLock]] =
    ZIO.effect(Option(channel.tryLock(position, size, shared)).map(new FileLock(_))).refineToOrDie[IOException]

}

object FileChannel {

  @silent
  def open(
    path: Path,
    options: Set[_ <: OpenOption],
    attrs: FileAttribute[_]*
  ): Managed[IOException, FileChannel] =
    IO.effect(new FileChannel(JFileChannel.open(path.javaPath, options.asJava, attrs: _*)))
      .refineToOrDie[IOException]
      .toNioManaged

  def open(path: Path, options: OpenOption*): Managed[IOException, FileChannel] =
    IO.effect(new FileChannel(JFileChannel.open(path.javaPath, options: _*)))
      .refineToOrDie[IOException]
      .toNioManaged

  def fromJava(javaFileChannel: JFileChannel): FileChannel = new FileChannel(javaFileChannel)

  type MapMode = JFileChannel.MapMode

  object MapMode {
    def READ_ONLY: FileChannel.MapMode  = JFileChannel.MapMode.READ_ONLY
    def READ_WRITE: FileChannel.MapMode = JFileChannel.MapMode.READ_WRITE
    def PRIVATE: FileChannel.MapMode    = JFileChannel.MapMode.PRIVATE
  }
}
