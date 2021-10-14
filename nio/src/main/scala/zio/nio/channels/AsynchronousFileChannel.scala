package zio.nio
package channels

import zio._
import zio.clock.Clock
import zio.nio.file.Path
import zio.stream.{Stream, ZSink, ZStream}

import java.io.{EOFException, IOException}
import java.nio.channels.{AsynchronousFileChannel => JAsynchronousFileChannel, FileLock => JFileLock}
import java.nio.file.OpenOption
import java.nio.file.attribute.FileAttribute
import scala.concurrent.ExecutionContextExecutorService
import scala.jdk.CollectionConverters._

final class AsynchronousFileChannel(protected val channel: JAsynchronousFileChannel) extends Channel {

  import AsynchronousByteChannel.effectAsyncChannel

  def force(metaData: Boolean): IO[IOException, Unit] = IO.effect(channel.force(metaData)).refineToOrDie[IOException]

  def lock(position: Long = 0L, size: Long = Long.MaxValue, shared: Boolean = false): IO[IOException, FileLock] =
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
  def read(dst: ByteBuffer, position: Long): IO[IOException, Int] =
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
  def readChunk(capacity: Int, position: Long): IO[IOException, Chunk[Byte]] =
    for {
      b     <- Buffer.byte(capacity)
      _     <- read(b, position)
      _     <- b.flip
      chunk <- b.getChunk()
    } yield chunk

  def size: IO[IOException, Long] = IO.effect(channel.size()).refineToOrDie[IOException]

  def truncate(size: Long): IO[IOException, Unit] = IO.effect(channel.truncate(size)).refineToOrDie[IOException].unit

  def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[IOException, FileLock] =
    IO.effect(new FileLock(channel.tryLock(position, size, shared))).refineToOrDie[IOException]

  def write(src: ByteBuffer, position: Long): IO[IOException, Int] =
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
  def writeChunk(src: Chunk[Byte], position: Long): IO[IOException, Unit] =
    Buffer.byte(src).flatMap { b =>
      def go(pos: Long): IO[IOException, Unit] =
        write(b, pos).flatMap { bytesWritten =>
          b.hasRemaining.flatMap {
            case true  => go(pos + bytesWritten.toLong)
            case false => IO.unit
          }
        }
      go(position)
    }

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
  def stream(position: Long, bufferConstruct: UIO[ByteBuffer] = Buffer.byte(5000)): Stream[IOException, Byte] =
    ZStream {
      for {
        posRef <- Ref.makeManaged(position)
        buffer <- bufferConstruct.toManaged_
      } yield {
        val doRead = for {
          pos   <- posRef.get
          count <- read(buffer, pos)
          _     <- posRef.set(pos + count.toLong)
          _     <- buffer.flip
          chunk <- buffer.getChunk()
          _     <- buffer.clear
        } yield chunk
        doRead.mapError {
          case _: EOFException => None
          case e               => Some(e)
        }
      }
    }

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
    bufferConstruct: UIO[ByteBuffer] = Buffer.byte(5000)
  ): ZSink[Clock, IOException, Byte, Byte, Long] =
    ZSink {
      for {
        buffer <- bufferConstruct.toManaged_
        posRef <- Ref.makeManaged(position)
      } yield (_: Option[Chunk[Byte]]).map { chunk =>
        def doWrite(currentPos: Long, c: Chunk[Byte]): ZIO[Clock, IOException, Long] = {
          val x = for {
            remaining <- buffer.putChunk(c)
            _         <- buffer.flip
            count <- ZStream
                       .repeatEffectWith(
                         write(buffer, currentPos),
                         Schedule.recurWhileM(Function.const(buffer.hasRemaining))
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
          newPos     <- doWrite(currentPos, chunk).catchAll(e => buffer.getChunk().flatMap(ZSink.Push.fail(e, _)))
          _          <- posRef.set(newPos)
        } yield ()

      }
        .getOrElse(
          posRef.get.flatMap[Any, (Either[IOException, Long], Chunk[Byte]), Unit](finalPos =>
            ZSink.Push.emit(finalPos - position, Chunk.empty)
          )
        )
    }

}

object AsynchronousFileChannel {

  def open(file: Path, options: OpenOption*): Managed[IOException, AsynchronousFileChannel] =
    IO.effect(new AsynchronousFileChannel(JAsynchronousFileChannel.open(file.javaPath, options: _*)))
      .refineToOrDie[IOException]
      .toNioManaged

  def open(
    file: Path,
    options: Set[OpenOption],
    executor: Option[ExecutionContextExecutorService],
    attrs: Set[FileAttribute[_]]
  ): Managed[IOException, AsynchronousFileChannel] =
    IO.effect(
      new AsynchronousFileChannel(
        JAsynchronousFileChannel.open(file.javaPath, options.asJava, executor.orNull, attrs.toSeq: _*)
      )
    ).refineToOrDie[IOException]
      .toNioManaged

  def fromJava(javaAsynchronousFileChannel: JAsynchronousFileChannel): AsynchronousFileChannel =
    new AsynchronousFileChannel(javaAsynchronousFileChannel)

}
