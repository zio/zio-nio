package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ AsynchronousFileChannel => JAsynchronousFileChannel, FileLock => JFileLock }
import java.nio.file.attribute.FileAttribute
import java.nio.file.OpenOption

import zio.interop.javaz._
import zio.nio.core.{ Buffer, ByteBuffer }
import zio.nio.core.channels.FileLock
import zio.nio.core.file.Path
import zio.{ Chunk, IO, Managed, ZIO }
import zio.nio.core.RichInt

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContextExecutorService

class AsynchronousFileChannel(protected val channel: JAsynchronousFileChannel) extends Channel {

  final def force(metaData: Boolean): IO[IOException, Unit] =
    IO.effect(channel.force(metaData)).refineToOrDie[IOException]

  final def lock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false): IO[Exception, FileLock] =
    effectAsyncWithCompletionHandler[JFileLock](channel.lock(position, size, shared, (), _))
      .map(FileLock.fromJava(_))
      .refineToOrDie[Exception]

  final def read(dst: ByteBuffer, position: Long): IO[Exception, Option[Int]] =
    dst.withJavaBuffer { buf =>
      effectAsyncWithCompletionHandler[Integer](channel.read(buf, position, (), _))
        .map(_.intValue.eofCheck)
        .refineToOrDie[Exception]
    }

  final def readChunk(capacity: Int, position: Long): IO[Exception, Option[Chunk[Byte]]] =
    (for {
      b     <- Buffer.byte(capacity)
      _     <- read(b, position).some
      _     <- b.flip
      chunk <- b.getChunk()
    } yield chunk).optional

  final val size: IO[IOException, Long] =
    IO.effect(channel.size()).refineToOrDie[IOException]

  final def truncate(size: Long): IO[IOException, Unit] =
    IO.effect(channel.truncate(size)).refineToOrDie[IOException].unit

  final def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[IOException, FileLock] =
    IO.effect(FileLock.fromJava(channel.tryLock(position, size, shared))).refineToOrDie[IOException]

  final def write(src: ByteBuffer, position: Long): IO[Exception, Int] =
    src.withJavaBuffer { buf =>
      effectAsyncWithCompletionHandler[Integer](channel.write(buf, position, (), _))
        .map(_.intValue)
        .refineToOrDie[Exception]
    }

  final def writeChunk(src: Chunk[Byte], position: Long): IO[Exception, Int] =
    for {
      b <- Buffer.byte(src)
      r <- write(b, position)
    } yield r
}

object AsynchronousFileChannel {

  def open(file: Path, options: OpenOption*): Managed[IOException, AsynchronousFileChannel] = {
    val open = ZIO
      .effect(new AsynchronousFileChannel(JAsynchronousFileChannel.open(file.javaPath, options: _*)))
      .refineToOrDie[IOException]

    Managed.make(open)(_.close.orDie)
  }

  def openWithExecutor(
    file: Path,
    options: Set[_ <: OpenOption],
    executor: Option[ExecutionContextExecutorService],
    attrs: Set[FileAttribute[_]] = Set.empty
  ): Managed[IOException, AsynchronousFileChannel] = {
    val open = ZIO
      .effect(
        new AsynchronousFileChannel(
          JAsynchronousFileChannel.open(file.javaPath, options.asJava, executor.orNull, attrs.toSeq: _*)
        )
      )
      .refineToOrDie[IOException]

    Managed.make(open)(_.close.orDie)
  }
}
