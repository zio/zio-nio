package zio.nio
package channels
import zio._
import zio.nio.file.Path

import java.io.IOException
import java.nio.channels.{AsynchronousFileChannel => JAsynchronousFileChannel}
import java.nio.file.OpenOption
import java.nio.file.attribute.FileAttribute
import scala.concurrent.ExecutionContextExecutorService
import scala.jdk.CollectionConverters._

final class AsynchronousFileChannel(protected val channel: JAsynchronousFileChannel) extends Channel {

  def force(metaData: Boolean)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.force(metaData)).refineToOrDie[IOException]

  def size(implicit trace: Trace): IO[IOException, Long] = ZIO.attempt(channel.size()).refineToOrDie[IOException]

  def truncate(size: Long)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.truncate(size)).refineToOrDie[IOException].unit

  def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  )(implicit trace: Trace): IO[IOException, FileLock] =
    ZIO.attempt(new FileLock(channel.tryLock(position, size, shared))).refineToOrDie[IOException]

}

object AsynchronousFileChannel {

  def open(file: Path, options: OpenOption*)(implicit
    trace: Trace
  ): ZIO[Scope, IOException, AsynchronousFileChannel] =
    ZIO
      .attempt(new AsynchronousFileChannel(JAsynchronousFileChannel.open(file.javaPath, options: _*)))
      .refineToOrDie[IOException]
      .toNioScoped

  def open(
    file: Path,
    options: Set[OpenOption],
    executor: Option[ExecutionContextExecutorService],
    attrs: Set[FileAttribute[_]]
  )(implicit trace: Trace): ZIO[Scope, IOException, AsynchronousFileChannel] =
    ZIO
      .attempt(
        new AsynchronousFileChannel(
          JAsynchronousFileChannel.open(file.javaPath, options.asJava, executor.orNull, attrs.toSeq: _*)
        )
      )
      .refineToOrDie[IOException]
      .toNioScoped

  def fromJava(javaAsynchronousFileChannel: JAsynchronousFileChannel): AsynchronousFileChannel =
    new AsynchronousFileChannel(javaAsynchronousFileChannel)

}
