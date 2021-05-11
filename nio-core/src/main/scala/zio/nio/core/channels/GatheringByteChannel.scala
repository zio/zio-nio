package zio.nio.core.channels

import zio.nio.core.{ Buffer, ByteBuffer }
import zio.{ Chunk, IO }

import java.io.IOException
import java.nio.channels.{ GatheringByteChannel => JGatheringByteChannel }
import java.nio.{ ByteBuffer => JByteBuffer }

/**
 * A channel that can write bytes from a sequence of buffers.
 */
trait GatheringByteChannel extends Channel {

  import GatheringByteChannel._

  override protected[channels] val channel: JGatheringByteChannel

  final def write(srcs: List[ByteBuffer]): IO[IOException, Long] =
    IO.effect(channel.write(unwrap(srcs))).refineToOrDie[IOException]

  final def write(src: ByteBuffer): IO[IOException, Int] =
    IO.effect(channel.write(src.byteBuffer)).refineToOrDie[IOException]

  /**
   * Writes a list of chunks, in order.
   *
   * Multiple writes may be performed in order to write all the chunks.
   */
  final def writeChunks(srcs: List[Chunk[Byte]]): IO[IOException, Unit] =
    for {
      bs <- IO.foreach(srcs)(Buffer.byte)
      _  <- {
        // Handle partial writes by dropping buffers where `hasRemaining` returns false,
        // meaning they've been completely written
        def go(buffers: List[ByteBuffer]): IO[IOException, Unit] =
          write(buffers).flatMap { _ =>
            IO.foreach(buffers)(b => b.hasRemaining.map(_ -> b)).flatMap { pairs =>
              val remaining = pairs.dropWhile(!_._1).map(_._2)
              if (remaining.isEmpty) IO.unit else go(remaining)
            }
          }
        go(bs)
      }
    } yield ()

  /**
   * Writes a chunk of bytes.
   *
   * Multiple writes may be performed to write the entire chunk.
   */
  final def writeChunk(src: Chunk[Byte]): IO[IOException, Unit] = writeChunks(List(src))

}

object GatheringByteChannel {

  private def unwrap(srcs: List[ByteBuffer]): Array[JByteBuffer] = srcs.map(d => d.byteBuffer).toArray

}
