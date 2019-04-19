package scalaz.nio.channels

import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{
  CompletionHandler,
  FileLock,
  AsynchronousFileChannel => JAsynchronousFileChannel
}

import scalaz.zio.{ Chunk, IO, JustExceptions, Task, UIO }
import scalaz.nio.Buffer
import java.nio.file.{ OpenOption, Path }
import java.nio.file.attribute.FileAttribute
import java.util.concurrent.ExecutorService

import scalaz.zio.interop.javaconcurrent._

import scala.collection.JavaConverters._

class AsynchronousFileChannel(private val channel: JAsynchronousFileChannel) {

  final def close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineOrDie(JustExceptions)

  final def force(metaData: Boolean): IO[Exception, Unit] =
    IO.effect(channel.force(metaData)).refineOrDie(JustExceptions)

  final val lock: IO[Throwable, FileLock] =
    Task.fromFutureJava(() => channel.lock())

  final def lock[A](attachment: A, handler: CompletionHandler[FileLock, A]): IO[Exception, Unit] =
    IO.effect(channel.lock(attachment, handler)).refineOrDie(JustExceptions)

  final def lock(position: Long, size: Long, shared: Boolean): IO[Throwable, FileLock] =
    Task.fromFutureJava(() => channel.lock(position, size, shared))

  final def lock[A](
    position: Long,
    size: Long,
    shared: Boolean,
    attachment: A,
    handler: CompletionHandler[FileLock, A]
  ): IO[Exception, Unit] =
    IO.effect(channel.lock(position, size, shared, attachment, handler)).refineOrDie(JustExceptions)

  final private[nio] def readBuffer(dst: Buffer[Byte], position: Long): IO[Throwable, Integer] =
    Task.fromFutureJava(() => channel.read(dst.buffer.asInstanceOf[JByteBuffer], position))

  final def read(capacity: Int, position: Long): IO[Throwable, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- readBuffer(b, position)
      a <- b.array
      r = Chunk.fromArray(a)
    } yield r

  final private[nio] def readBuffer[A](
    dst: Buffer[Byte],
    position: Long,
    attachment: A,
    handler: CompletionHandler[Integer, A]
  ): IO[Exception, Unit] =
    IO.effect(
        channel.read(dst.buffer.asInstanceOf[JByteBuffer], position, attachment, handler)
      )
      .refineOrDie(JustExceptions)

  final def read[A](
    capacity: Int,
    position: Long,
    attachment: A,
    handler: CompletionHandler[Integer, A]
  ): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- readBuffer(b, position, attachment, handler)
      a <- b.array
      r = Chunk.fromArray(a)
    } yield r

  final val size: IO[Exception, Long] =
    IO.effect(channel.size()).refineOrDie(JustExceptions)

  final def truncate(size: Long): IO[Exception, AsynchronousFileChannel] =
    IO.effect(new AsynchronousFileChannel(channel.truncate(size))).refineOrDie(JustExceptions)

  final val tryLock: IO[Exception, FileLock] =
    IO.effect(channel.tryLock()).refineOrDie(JustExceptions)

  final def tryLock(position: Long, size: Long, shared: Boolean): IO[Exception, FileLock] =
    IO.effect(channel.tryLock(position, size, shared)).refineOrDie(JustExceptions)

  final private[nio] def writeBuffer(src: Buffer[Byte], position: Long): IO[Throwable, Integer] =
    Task.fromFutureJava(() => channel.write(src.buffer.asInstanceOf[JByteBuffer], position))

  final def write(src: Chunk[Byte], position: Long): IO[Throwable, Integer] =
    for {
      b <- Buffer.byte(src)
      r <- writeBuffer(b, position)
    } yield r

  final private[nio] def writeBuffer[A](
    src: Buffer[Byte],
    position: Long,
    attachment: A,
    handler: CompletionHandler[Integer, A]
  ): IO[Exception, Unit] =
    IO.effect(
        channel.write(src.buffer.asInstanceOf[JByteBuffer], position, attachment, handler)
      )
      .refineOrDie(JustExceptions)

  final def write[A](
    src: Chunk[Byte],
    position: Long,
    attachment: A,
    handler: CompletionHandler[Integer, A]
  ): IO[Exception, Unit] =
    for {
      b <- Buffer.byte(src)
      _ <- writeBuffer(b, position, attachment, handler)
    } yield ()

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
      .refineOrDie(JustExceptions)

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
      .refineOrDie(JustExceptions)

}
