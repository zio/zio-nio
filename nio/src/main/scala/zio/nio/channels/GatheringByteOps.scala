package zio.nio.channels

import zio._
import zio.nio.Buffer.byteFromJava
import zio.nio.{Buffer, ByteBuffer}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.{ZSink, ZStream}

import java.io.IOException
import java.nio.channels.{GatheringByteChannel => JGatheringByteChannel}
import java.nio.{ByteBuffer => JByteBuffer}

/**
 * A channel that can write bytes from a sequence of buffers.
 */
trait GatheringByteOps {

  import GatheringByteOps._

  protected[channels] def channel: JGatheringByteChannel

  final def write(srcs: List[ByteBuffer])(implicit trace: Trace): IO[IOException, Long] =
    ZIO.attempt(channel.write(unwrap(srcs))).refineToOrDie[IOException]

  final def write(src: ByteBuffer)(implicit trace: Trace): IO[IOException, Int] =
    ZIO.attempt(channel.write(src.buffer)).refineToOrDie[IOException]

  /**
   * Writes a list of chunks, in order.
   *
   * Multiple writes may be performed in order to write all the chunks.
   */
  final def writeChunks(srcs: List[Chunk[Byte]])(implicit trace: Trace): IO[IOException, Unit] =
    for {
      bs <- ZIO.foreach(srcs)(Buffer.byte)
      _ <- {
        // Handle partial writes by dropping buffers where `hasRemaining` returns false,
        // meaning they've been completely written
        def go(buffers: List[ByteBuffer])(implicit trace: Trace): IO[IOException, Unit] =
          for {
            _     <- write(buffers)
            pairs <- ZIO.foreach(buffers)(b => b.hasRemaining.map(_ -> b))
            _ <- {
              val remaining = pairs.dropWhile(!_._1).map(_._2)
              go(remaining).unless(remaining.isEmpty)
            }
          } yield ()
        go(bs)
      }
    } yield ()

  /**
   * Writes a chunk of bytes.
   *
   * Multiple writes may be performed to write the entire chunk.
   */
  final def writeChunk(src: Chunk[Byte])(implicit trace: Trace): IO[IOException, Unit] = writeChunks(List(src))

  def sink()(implicit trace: Trace): ZSink[Any, IOException, Byte, Byte, Long] = sink(Buffer.byte(5000))

  /**
   * A sink that will write all the bytes it receives to this channel. The sink's result is the number of bytes written.
   * '''Note:''' This method does not work well with a channel in non-blocking mode, as it will busy-wait whenever the
   * channel is not ready for writes. The returned sink should be run within the context of a `useBlocking` call for
   * correct blocking and interruption support.
   *
   * @param bufferConstruct
   *   Optional, overrides how to construct the buffer used to transfer bytes received by the sink to this channel. By
   *   default a heap buffer is used, but a direct buffer will usually perform better.
   */
  def sink(
    bufferConstruct: UIO[ByteBuffer]
  )(implicit trace: Trace): ZSink[Any, IOException, Byte, Byte, Long] =
    ZSink.fromPush {
      for {
        buffer   <- bufferConstruct
        countRef <- Ref.make(0L)
      } yield (_: Option[Chunk[Byte]]).map { chunk =>
        def doWrite(total: Int, c: Chunk[Byte])(implicit trace: Trace): ZIO[Any, IOException, Int] = {
          val x = for {
            remaining <- buffer.putChunk(c)
            _         <- buffer.flip
            count <-
              ZStream
                .repeatZIOWithSchedule(write(buffer), Schedule.recurWhileZIO(Function.const(buffer.hasRemaining)))
                .runSum
            _ <- buffer.clear
          } yield (count + total, remaining)
          // can't safely recurse in for expression
          x.flatMap {
            case (result, remaining) if remaining.isEmpty => ZIO.succeed(result)
            case (result, remaining)                      => doWrite(result, remaining)
          }
        }

        doWrite(0, chunk).foldZIO(
          e => buffer.getChunk().flatMap(chunk => ZIO.fail((Left(e), chunk))),
          count => countRef.update(_ + count.toLong)
        )
      }
        .getOrElse(
          countRef.get.flatMap[Any, (Either[IOException, Long], Chunk[Byte]), Unit](count =>
            ZIO.fail((Right(count), Chunk.empty))
          )
        )
    }

}

object GatheringByteOps {

  private def unwrap(srcs: List[ByteBuffer]): Array[JByteBuffer] = srcs.map(d => d.buffer).toArray

}
