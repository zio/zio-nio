package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ FileChannel => JFileChannel }
import java.nio.file.attribute.FileAttribute
import java.nio.file.{ OpenOption, Path }

import scala.collection.JavaConverters._
import zio.{ IO, ZIO }
import zio.blocking.Blocking
import zio.nio.{ ByteBuffer, MappedByteBuffer }

final class FileChannel private[channels] (override protected[channels] val channel: JFileChannel)
    extends GatheringByteChannel
    with ScatteringByteChannel {

  def position: IO[IOException, Long] = IO.effect(channel.position()).refineToOrDie[IOException]

  def size: IO[IOException, Long] = IO.effect(channel.size()).refineToOrDie[IOException]

  def truncate(size: Long): ZIO[Blocking, Exception, Unit] =
    ZIO.accessM[Blocking](_.blocking.effectBlocking(channel.truncate(size))).unit.refineToOrDie[Exception]

  def force(metadata: Boolean): ZIO[Blocking, IOException, Unit] =
    ZIO.accessM[Blocking](_.blocking.effectBlocking(channel.force(metadata))).refineToOrDie[IOException]

  def transferTo(position: Long, count: Long, target: GatheringByteChannel): ZIO[Blocking, Exception, Long] =
    ZIO
      .accessM[Blocking](_.blocking.effectBlocking(channel.transferTo(position, count, target.channel)))
      .refineToOrDie[Exception]

  def transferFrom(src: ScatteringByteChannel, position: Long, count: Long): ZIO[Blocking, Exception, Long] =
    ZIO
      .accessM[Blocking](_.blocking.effectBlocking(channel.transferFrom(src.channel, position, count)))
      .refineToOrDie[Exception]

  def read(dst: ByteBuffer, position: Long): ZIO[Blocking, Exception, Int] =
    ZIO
      .accessM[Blocking] {
        _.blocking.blocking {
          dst.withJavaBuffer[Any, Throwable, Int](buffer => IO.effect(channel.read(buffer, position)))
        }
      }
      .refineToOrDie[Exception]

  def write(src: ByteBuffer, position: Long): ZIO[Blocking, Exception, Int] =
    ZIO
      .accessM[Blocking] {
        _.blocking.blocking {
          src.withJavaBuffer[Any, Throwable, Int](buffer => IO.effect(channel.write(buffer, position)))
        }
      }
      .refineToOrDie[Exception]

  def map(mode: JFileChannel.MapMode, position: Long, size: Long): ZIO[Blocking, Exception, MappedByteBuffer] =
    ZIO
      .accessM[Blocking](_.blocking.effectBlocking(new MappedByteBuffer(channel.map(mode, position, size))))
      .refineToOrDie[Exception]

  def lock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): ZIO[Blocking, Exception, FileLock] =
    ZIO
      .accessM[Blocking](_.blocking.effectBlocking(new FileLock(channel.lock(position, size, shared))))
      .refineToOrDie[Exception]

  def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[Exception, Option[FileLock]] =
    ZIO.effect(Option(channel.tryLock(position, size, shared)).map(new FileLock(_))).refineToOrDie[Exception]

}

object FileChannel {

  def open(path: Path, options: Set[_ <: OpenOption], attrs: FileAttribute[_]*): IO[Exception, FileChannel] =
    IO.effect(new FileChannel(JFileChannel.open(path, options.asJava, attrs: _*))).refineToOrDie[Exception]

  def open(path: Path, options: OpenOption*): IO[Exception, FileChannel] =
    IO.effect(new FileChannel(JFileChannel.open(path, options: _*))).refineToOrDie[Exception]

  type MapMode = JFileChannel.MapMode

  object MapMode {
    def READ_ONLY  = JFileChannel.MapMode.READ_ONLY
    def READ_WRITE = JFileChannel.MapMode.READ_WRITE
    def PRIVATE    = JFileChannel.MapMode.PRIVATE
  }
}
