package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ AsynchronousFileChannel => JAsynchronousFileChannel, FileLock => JFileLock }
import java.nio.file.attribute.FileAttribute
import java.nio.file.{ OpenOption, Path }
import java.util.concurrent.ExecutorService

import zio.blocking.Blocking
import zio.nio.{ Buffer, ByteBuffer }
import zio.{ Chunk, IO, ZIO, ZManaged }

import scala.collection.JavaConverters._

class AsynchronousFileChannel(protected val channel: JAsynchronousFileChannel) extends Channel {

  import AsynchronousChannel.wrap

  final def force(metaData: Boolean): IO[IOException, Unit] =
    IO.effect(channel.force(metaData)).refineToOrDie[IOException]

  final def lock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false): IO[Exception, FileLock] =
    wrap[JFileLock](channel.lock(position, size, shared, (), _)).map(new FileLock(_))

  final private[nio] def readBuffer(dst: ByteBuffer, position: Long): IO[Exception, Int] =
    dst.withJavaBuffer { buf =>
      wrap[Integer](channel.read(buf, position, (), _))
        .map(_.intValue)
    }

  final def read(capacity: Int, position: Long): IO[Exception, Chunk[Byte]] =
    for {
      b     <- Buffer.byte(capacity)
      count <- readBuffer(b, position)
      a     <- b.array
    } yield Chunk.fromArray(a).take(math.max(count, 0))

  final val size: IO[IOException, Long] =
    IO.effect(channel.size()).refineToOrDie[IOException]

  final def truncate(size: Long): IO[Exception, Unit] =
    IO.effect(channel.truncate(size)).refineToOrDie[Exception].unit

  final def tryLock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false): IO[Exception, FileLock] =
    IO.effect(new FileLock(channel.tryLock(position, size, shared))).refineToOrDie[Exception]

  final private[nio] def writeBuffer(src: ByteBuffer, position: Long): IO[Exception, Int] =
    src.withJavaBuffer { buf =>
      wrap[Integer](channel.write(buf, position, (), _))
        .map(_.intValue)
    }

  final def write(src: Chunk[Byte], position: Long): IO[Exception, Int] =
    for {
      b <- Buffer.byte(src)
      r <- writeBuffer(b, position)
    } yield r

}

object AsynchronousFileChannel {

  def open(file: Path, options: OpenOption*): ZManaged[Blocking, Exception, AsynchronousFileChannel] = {
    val open = ZIO
      .accessM[Blocking] {
        _.blocking.effectBlocking(new AsynchronousFileChannel(JAsynchronousFileChannel.open(file, options: _*)))
      }
      .refineToOrDie[Exception]

    ZManaged.make(open)(_.close.orDie)
  }

  def open(
    file: Path,
    options: Set[_ <: OpenOption],
    executor: Option[ExecutorService] = None,
    attrs: Set[FileAttribute[_]] = Set.empty
  ): ZManaged[Blocking, Exception, AsynchronousFileChannel] = {
    val open = ZIO
      .accessM[Blocking] {
        _.blocking.effectBlocking(
          new AsynchronousFileChannel(
            JAsynchronousFileChannel.open(file, options.asJava, executor.orNull, attrs.toSeq: _*)
          )
        )
      }
      .refineToOrDie[Exception]

    ZManaged.make(open)(_.close.orDie)
  }

}
