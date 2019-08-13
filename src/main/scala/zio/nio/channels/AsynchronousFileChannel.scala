package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ FileLock => JFileLock, AsynchronousFileChannel => JAsynchronousFileChannel }
import java.nio.file.attribute.FileAttribute
import java.nio.file.{ OpenOption, Path }
import java.util.concurrent.ExecutorService

import zio.nio.{ Buffer, ByteBuffer }
import zio.{ Chunk, IO, UIO }

import scala.collection.JavaConverters._

class AsynchronousFileChannel(private val channel: JAsynchronousFileChannel) {

  import AsynchronousChannel.wrap

  final def close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineToOrDie[Exception]

  final def force(metaData: Boolean): IO[IOException, Unit] =
    IO.effect(channel.force(metaData)).refineToOrDie[IOException]

  final def lock: IO[Exception, FileLock] =
    wrap[JFileLock](channel.lock((), _)).map(FileLock.fromJFileLock(_, channel = this))

  final def lock(position: Long, size: Long, shared: Boolean): IO[Exception, FileLock] =
    wrap[JFileLock](channel.lock(position, size, shared, (), _)).map(FileLock.fromJFileLock(_, channel = this))

  final private[nio] def readBuffer(dst: ByteBuffer, position: Long): IO[Exception, Int] =
    dst.withJavaBuffer { buf =>
      wrap[Integer](channel.read(buf, position, (), _))
        .map(_.intValue)
    }

  final def read(capacity: Int, position: Long): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- readBuffer(b, position)
      a <- b.array
      r = Chunk.fromArray(a)
    } yield r

  final val size: IO[IOException, Long] =
    IO.effect(channel.size()).refineToOrDie[IOException]

  final def truncate(size: Long): IO[Exception, Unit] =
    IO.effect(channel.truncate(size)).refineToOrDie[Exception].unit

  final val tryLock: IO[Exception, FileLock] =
    IO.effect(channel.tryLock()).refineToOrDie[Exception].map(FileLock.fromJFileLock(_, channel = this))

  final def tryLock(position: Long, size: Long, shared: Boolean): IO[Exception, FileLock] =
    IO.effect(channel.tryLock(position, size, shared))
      .refineToOrDie[Exception]
      .map(FileLock.fromJFileLock(_, channel = this))

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

  /**
   * Tells whether or not this channel is open.
   */
  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)
}

object AsynchronousFileChannel {

  def open[A <: OpenOption](file: Path, options: Set[A]): IO[Exception, AsynchronousFileChannel] =
    IO.effect(
        new AsynchronousFileChannel(JAsynchronousFileChannel.open(file, options.toSeq: _*))
      )
      .refineToOrDie[Exception]

  def open(
    file: Path,
    options: Set[OpenOption],
    executor: Option[ExecutorService],
    attrs: Set[FileAttribute[_]]
  ): IO[Exception, AsynchronousFileChannel] =
    IO.effect(
        new AsynchronousFileChannel(
          JAsynchronousFileChannel.open(file, options.asJava, executor.orNull, attrs.toSeq: _*)
        )
      )
      .refineToOrDie[Exception]

}
