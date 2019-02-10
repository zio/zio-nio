package scalaz.nio.channels

import java.nio.channels.{
  AsynchronousFileChannel => JAsynchronousFileChannel,
  CompletionHandler,
  FileLock
}
import java.nio.file.{ OpenOption, Path }
import java.nio.file.attribute.FileAttribute
import java.util.concurrent.ExecutorService

import scalaz.nio.ByteBuffer
import scalaz.zio.interop.javaconcurrent._
import scalaz.zio.IO

import scala.collection.JavaConverters._

class AsynchronousFileChannel(private val channel: JAsynchronousFileChannel) {

  final def close: IO[Exception, Unit] =
    IO.syncException(channel.close())

  final def force(metaData: Boolean): IO[Exception, Unit] =
    IO.syncException(channel.force(metaData))

  final val lock: IO[Throwable, FileLock] =
    IO.fromFutureJava(() => channel.lock())

  final def lock[A](attachment: A, handler: CompletionHandler[FileLock, A]): IO[Exception, Unit] =
    IO.syncException(channel.lock(attachment, handler))

  final def lock(position: Long, size: Long, shared: Boolean): IO[Throwable, FileLock] =
    IO.fromFutureJava(() => channel.lock(position, size, shared))

  final def lock[A](
    position: Long,
    size: Long,
    shared: Boolean,
    attachment: A,
    handler: CompletionHandler[FileLock, A]
  ): IO[Exception, Unit] =
    IO.syncException(channel.lock(position, size, shared, attachment, handler))

  final def read(dst: ByteBuffer, position: Long): IO[Throwable, Integer] =
    IO.fromFutureJava(() => channel.read(dst.byteBuffer, position))

  final def read[A](
    dst: ByteBuffer,
    position: Long,
    attachment: A,
    handler: CompletionHandler[Integer, A]
  ): IO[Exception, Unit] =
    IO.syncException(channel.read(dst.byteBuffer, position, attachment, handler))

  final val size: IO[Exception, Long] =
    IO.syncException(channel.size())

  final def truncate(size: Long): IO[Exception, AsynchronousFileChannel] =
    IO.syncException(new AsynchronousFileChannel(channel.truncate(size)))

  final val tryLock: IO[Exception, FileLock] =
    IO.syncException(channel.tryLock())

  final def tryLock(position: Long, size: Long, shared: Boolean): IO[Exception, FileLock] =
    IO.syncException(channel.tryLock(position, size, shared))

  final def write(src: ByteBuffer, position: Long): IO[Throwable, Integer] =
    IO.fromFutureJava(() => channel.write(src.byteBuffer, position))

  final def write[A](
    src: ByteBuffer,
    position: Long,
    attachment: A,
    handler: CompletionHandler[Integer, A]
  ): IO[Exception, Unit] =
    IO.syncException(channel.write(src.byteBuffer, position, attachment, handler))

}

object AsynchronousFileChannel {

  def open[A <: OpenOption](file: Path, options: Set[A]): IO[Exception, AsynchronousFileChannel] =
    IO.syncException(
      new AsynchronousFileChannel(JAsynchronousFileChannel.open(file, options.toSeq: _*))
    )

  def open(
    file: Path,
    options: Set[OpenOption],
    executor: Option[ExecutorService],
    attrs: Set[FileAttribute[_]]
  ): IO[Exception, AsynchronousFileChannel] =
    IO.syncException(
      new AsynchronousFileChannel(
        JAsynchronousFileChannel.open(file, options.asJava, executor.orNull, attrs.toSeq: _*)
      )
    )

}
