package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ AsynchronousFileChannel => JAsynchronousFileChannel, FileLock => JFileLock }
import java.nio.file.attribute.FileAttribute
import java.nio.file.OpenOption

import com.github.ghik.silencer.silent
import zio.nio.file.Path
import zio.nio.{ Buffer, ByteBuffer }
import zio.{ Chunk, IO, Managed, ZIO }

import scala.collection.JavaConverters._
import zio.interop.javaz._

class AsynchronousFileChannel(protected val channel: JAsynchronousFileChannel) extends Channel {

  final def force(metaData: Boolean): IO[IOException, Unit] =
    IO.effect(channel.force(metaData)).refineToOrDie[IOException]

  final def lock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false): IO[Exception, FileLock] =
    withCompletionHandler[JFileLock](channel.lock(position, size, shared, (), _))
      .map(new FileLock(_))
      .refineToOrDie[Exception]

  final private[nio] def readBuffer(dst: ByteBuffer, position: Long): IO[Exception, Int] =
    dst.withJavaBuffer { buf =>
      withCompletionHandler[Integer](channel.read(buf, position, (), _))
        .map(_.intValue)
        .refineToOrDie[Exception]
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
      withCompletionHandler[Integer](channel.write(buf, position, (), _))
        .map(_.intValue)
        .refineToOrDie[Exception]
    }

  final def write(src: Chunk[Byte], position: Long): IO[Exception, Int] =
    for {
      b <- Buffer.byte(src)
      r <- writeBuffer(b, position)
    } yield r
}

object AsynchronousFileChannel {

  def open(file: Path, options: OpenOption*): Managed[Exception, AsynchronousFileChannel] = {
    val open = ZIO
      .effect(new AsynchronousFileChannel(JAsynchronousFileChannel.open(file.javaPath, options: _*)))
      .refineToOrDie[Exception]

    Managed.make(open)(_.close.orDie)
  }

  def openWithExecutor(
    file: Path,
    options: Set[_ <: OpenOption],
    attrs: Set[FileAttribute[_]] = Set.empty
  ): Managed[Exception, AsynchronousFileChannel] = {
    @silent
    val open = for {
      eces <- ZIO.runtime.map((runtime: zio.Runtime[Any]) => runtime.platform.executor.asECES)
      channel <- ZIO
                  .effect(
                    new AsynchronousFileChannel(
                      JAsynchronousFileChannel.open(file.javaPath, options.asJava, eces, attrs.toSeq: _*)
                    )
                  )
                  .refineToOrDie[Exception]
    } yield channel

    Managed.make(open)(_.close.orDie)
  }
}
