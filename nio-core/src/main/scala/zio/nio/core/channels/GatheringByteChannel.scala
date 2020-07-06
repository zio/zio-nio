package zio.nio.core.channels

import java.io.IOException
import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{ GatheringByteChannel => JGatheringByteChannel }

import zio.{ Chunk, IO, ZIO }
import zio.nio.core.{ Buffer, ByteBuffer }

/**
 * A channel that can write bytes from a sequence of buffers.
 */
trait GatheringByteChannel[R] extends Channel with WithEnv[R] {

  import GatheringByteChannel._

  override protected[channels] val channel: JGatheringByteChannel

  final def write(srcs: List[ByteBuffer]): ZIO[R, IOException, Long] =
    withEnv {
      IO.effect(channel.write(unwrap(srcs))).refineToOrDie[IOException]
    }

  final def write(src: ByteBuffer): ZIO[R, IOException, Int] =
    withEnv {
      IO.effect(channel.write(src.byteBuffer)).refineToOrDie[IOException]
    }

  /**
   * Writes a list of chunks, in order.
   *
   * Multiple writes may be performed in order to write all the chunks.
   */
  final def writeChunks(srcs: List[Chunk[Byte]]): ZIO[R, IOException, Unit] =
    for {
      bs <- IO.foreach(srcs)(Buffer.byte)
      _  <- {
        // Handle partial writes by dropping buffers where `hasRemaining` returns false,
        // meaning they've been completely written
        def go(buffers: List[ByteBuffer]): ZIO[R, IOException, Unit] =
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
  final def writeChunk(src: Chunk[Byte]): ZIO[R, IOException, Unit] = writeChunks(List(src))

}

object GatheringByteChannel {

  private def unwrap(srcs: List[ByteBuffer]): Array[JByteBuffer] =
    srcs.map(d => d.byteBuffer).toArray

}
