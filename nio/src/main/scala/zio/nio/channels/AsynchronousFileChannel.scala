package zio.nio
package channels
import zio._
import zio.nio.file.Path
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.{Stream, ZSink, ZStream}

import java.io.{EOFException, IOException}
import java.nio.channels.{AsynchronousFileChannel => JAsynchronousFileChannel, FileLock => JFileLock}
import java.nio.file.OpenOption
import java.nio.file.attribute.FileAttribute
import scala.concurrent.ExecutionContextExecutorService
import scala.jdk.CollectionConverters._

final class AsynchronousFileChannel(protected val channel: JAsynchronousFileChannel) extends Channel {

  import AsynchronousByteChannel.effectAsyncChannel

  def force(metaData: Boolean)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.force(metaData)).refineToOrDie[IOException]

  def lock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false)(implicit
    trace: Trace
  ): IO[IOException, FileLock] =
    effectAsyncChannel[JAsynchronousFileChannel, JFileLock](channel)(c => c.lock(position, size, shared, (), _))
      .map(new FileLock(_))

  /**
   * Reads data from this channel into buffer, returning the number of bytes read.
   *
   * Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   * @param position
   *   The file position at which the transfer is to begin; must be non-negative
   */
  def read(dst: ByteBuffer, position: Long)(implicit trace: Trace): IO[IOException, Int] =
    dst.withJavaBuffer { buf =>
      effectAsyncChannel[JAsynchronousFileChannel, Integer](channel)(c => c.read(buf, position, (), _))
        .flatMap(eofCheck(_))
    }

  /**
   * Reads data from this channel as a `Chunk`.
   *
   * Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   * @param position
   *   The file position at which the transfer is to begin; must be non-negative
   */
  def readChunk(capacity: Int, position: Long)(implicit trace: Trace): IO[IOException, Chunk[Byte]] =
    for {
      b     <- Buffer.byte(capacity)
      _     <- read(b, position)
      _     <- b.flip
      chunk <- b.getChunk()
    } yield chunk

  def size(implicit trace: Trace): IO[IOException, Long] = ZIO.attempt(channel.size()).refineToOrDie[IOException]

  def truncate(size: Long)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.truncate(size)).refineToOrDie[IOException].unit

  def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  )(implicit trace: Trace): IO[IOException, FileLock] =
    ZIO.attempt(new FileLock(channel.tryLock(position, size, shared))).refineToOrDie[IOException]

  def write(src: ByteBuffer, position: Long)(implicit trace: Trace): IO[IOException, Int] =
    src.withJavaBuffer { buf =>
      effectAsyncChannel[JAsynchronousFileChannel, Integer](channel)(c => c.write(buf, position, (), _))
        .map(_.intValue)
    }

  /**
   * Writes a chunk of bytes at a specified position in the file.
   *
   * More than one write operation may be performed to write the entire chunk.
   *
   * @param src
   *   The bytes to write.
   * @param position
   *   Where in the file to write.
   */
  def writeChunk(src: Chunk[Byte], position: Long)(implicit trace: Trace): IO[IOException, Unit] =
    Buffer.byte(src).flatMap { b =>
      def go(pos: Long)(implicit trace: Trace): IO[IOException, Unit] =
        write(b, pos).flatMap { bytesWritten =>
          b.hasRemaining.flatMap {
            case true  => go(pos + bytesWritten.toLong)
            case false => ZIO.unit
          }
        }
      go(position)
    }

  def stream(position: Long)(implicit trace: Trace): Stream[IOException, Byte] =
    stream(position, Buffer.byte(5000))

  /**
   * A `ZStream` that reads from this channel.
   *
   * The stream terminates without error if the channel reaches end-of-stream.
   *
   * @param position
   *   The position in the file the stream will read from.
   * @param bufferConstruct
   *   Optional, overrides how to construct the buffer used to transfer bytes read from this channel into the stream. By
   *   default a heap buffer is used, but a direct buffer will usually perform better.
   */
  def stream(position: Long, bufferConstruct: UIO[ByteBuffer])(implicit
    trace: Trace
  ): Stream[IOException, Byte] =
    ZStream.unwrapScoped {
      for {
        posRef <- Ref.make(position)
        buffer <- bufferConstruct
      } yield {
        val doRead = for {
          pos   <- posRef.get
          count <- read(buffer, pos)
          _     <- posRef.set(pos + count.toLong)
          _     <- buffer.flip
          chunk <- buffer.getChunk()
          _     <- buffer.clear
        } yield chunk
        ZStream.repeatZIOChunkOption(doRead.mapError {
          case _: EOFException => None
          case e               => Some(e)
        })
      }
    }

  def sink(position: Long)(implicit trace: Trace): ZSink[Any, IOException, Byte, Byte, Long] =
    sink(position, Buffer.byte(5000))

  /**
   * A sink that will write all the bytes it receives to this channel. The sink's result is the number of bytes written.
   *
   * @param position
   *   The position in the file the sink will write to.
   * @param bufferConstruct
   *   Optional, overrides how to construct the buffer used to transfer bytes received by the sink to this channel. By
   *   default a heap buffer is used, but a direct buffer will usually perform better.
   */
  def sink(
    position: Long,
    bufferConstruct: UIO[ByteBuffer]
  )(implicit trace: Trace): ZSink[Any, IOException, Byte, Byte, Long] =
    ZSink.fromPush {
      for {
        buffer <- bufferConstruct
        posRef <- Ref.make(position)
      } yield (_: Option[Chunk[Byte]]).map { chunk =>
        def doWrite(currentPos: Long, c: Chunk[Byte])(implicit trace: Trace): ZIO[Any, IOException, Long] = {
          val x = for {
            remaining <- buffer.putChunk(c)
            _         <- buffer.flip
            count <- ZStream
                       .repeatZIOWithSchedule(
                         write(buffer, currentPos),
                         Schedule.recurWhileZIO(Function.const(buffer.hasRemaining))
                       )
                       .runSum
            _ <- buffer.clear
          } yield (currentPos + count.toLong, remaining)
          // can't safely recurse in for expression
          x.flatMap {
            case (result, remaining) if remaining.isEmpty => ZIO.succeed(result)
            case (result, remaining)                      => doWrite(result, remaining)
          }
        }

        for {
          currentPos <- posRef.get
          newPos <-
            doWrite(currentPos, chunk).catchAll(e => buffer.getChunk().flatMap(chunk => ZIO.fail((Left(e), chunk))))
          _ <- posRef.set(newPos)
        } yield ()

      }
        .getOrElse(
          posRef.get.flatMap[Any, (Either[IOException, Long], Chunk[Byte]), Unit](finalPos =>
            ZIO.fail((Right(finalPos - position), Chunk.empty))
          )
        )
    }

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
