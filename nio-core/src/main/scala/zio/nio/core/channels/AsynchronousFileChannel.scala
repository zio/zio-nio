package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ AsynchronousFileChannel => JAsynchronousFileChannel, FileLock => JFileLock }
import java.nio.file.attribute.FileAttribute
import java.nio.file.OpenOption

import zio.{ Chunk, IO }
import zio.interop.javaz._
import zio.nio.core.{ Buffer, ByteBuffer, eofCheck }
import zio.nio.core.file.Path

import scala.concurrent.ExecutionContextExecutorService
import scala.jdk.CollectionConverters._

class AsynchronousFileChannel(protected val channel: JAsynchronousFileChannel) extends Channel {

  final def force(metaData: Boolean): IO[IOException, Unit] =
    IO.effect(channel.force(metaData)).refineToOrDie[IOException]

  final def lock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false): IO[Exception, FileLock] =
    effectAsyncWithCompletionHandler[JFileLock](channel.lock(position, size, shared, (), _))
      .map(new FileLock(_))
      .refineToOrDie[Exception]

  /**
   *  Reads data from this channel into buffer, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   *  @param position The file position at which the transfer is to begin; must be non-negative
   */
  final def read(dst: ByteBuffer, position: Long): IO[Exception, Int] =
    dst.withJavaBuffer { buf =>
      effectAsyncWithCompletionHandler[Integer](channel.read(buf, position, (), _))
        .refineToOrDie[Exception]
        .flatMap(eofCheck(_))
    }

  /**
   *  Reads data from this channel as a `Chunk`.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   *  @param position The file position at which the transfer is to begin; must be non-negative
   */
  final def readChunk(capacity: Int, position: Long): IO[Exception, Chunk[Byte]] =
    for {
      b     <- Buffer.byte(capacity)
      _     <- read(b, position)
      _     <- b.flip
      chunk <- b.getChunk()
    } yield chunk

  final val size: IO[IOException, Long] =
    IO.effect(channel.size()).refineToOrDie[IOException]

  final def truncate(size: Long): IO[IOException, Unit] =
    IO.effect(channel.truncate(size)).refineToOrDie[IOException].unit

  final def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[IOException, FileLock] =
    IO.effect(new FileLock(channel.tryLock(position, size, shared))).refineToOrDie[IOException]

  final def write(src: ByteBuffer, position: Long): IO[Exception, Int] =
    src.withJavaBuffer { buf =>
      effectAsyncWithCompletionHandler[Integer](channel.write(buf, position, (), _))
        .map(_.intValue)
        .refineToOrDie[Exception]
    }

  /**
   * Writes a chunk of bytes at a specified position in the file.
   *
   * More than one write operation may be performed to write the entire
   * chunk.
   *
   * @param src The bytes to write.
   * @param position Where in the file to write.
   */
  final def writeChunk(src: Chunk[Byte], position: Long): IO[Exception, Unit] =
    Buffer.byte(src).flatMap { b =>
      def go(pos: Long): IO[Exception, Unit] =
        write(b, pos).flatMap { bytesWritten =>
          b.hasRemaining.flatMap {
            case true  => go(pos + bytesWritten.toLong)
            case false => IO.unit
          }
        }
      go(position)
    }
}

object AsynchronousFileChannel {

  def open(file: Path, options: OpenOption*): IO[IOException, AsynchronousFileChannel] =
    IO.effect(
      new AsynchronousFileChannel(JAsynchronousFileChannel.open(file.javaPath, options: _*))
    ).refineToOrDie[IOException]

  def open(
    file: Path,
    options: Set[OpenOption],
    executor: Option[ExecutionContextExecutorService],
    attrs: Set[FileAttribute[_]]
  ): IO[IOException, AsynchronousFileChannel] =
    IO.effect(
      new AsynchronousFileChannel(
        JAsynchronousFileChannel.open(file.javaPath, options.asJava, executor.orNull, attrs.toSeq: _*)
      )
    ).refineToOrDie[IOException]

  def fromJava(javaAsynchronousFileChannel: JAsynchronousFileChannel): AsynchronousFileChannel =
    new AsynchronousFileChannel(javaAsynchronousFileChannel)
}
