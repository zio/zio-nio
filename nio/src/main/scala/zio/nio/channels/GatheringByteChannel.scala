package zio.nio.channels

import java.nio.channels.{ GatheringByteChannel => JGatheringByteChannel }
import java.nio.{ ByteBuffer => JByteBuffer }

import zio.nio.core.{ Buffer, ByteBuffer }
import zio.{ Chunk, IO }

trait GatheringByteChannel extends Channel {
  override protected[channels] val channel: JGatheringByteChannel

  final def write(srcs: List[ByteBuffer]): IO[Exception, Long] =
    IO.effect(channel.write(unwrap(srcs))).refineToOrDie[Exception]

  final def write(src: ByteBuffer): IO[Exception, Long] = write(List(src))

  final def writeChunks(srcs: List[Chunk[Byte]]): IO[Exception, Long] =
    for {
      bs <- IO.foreach(srcs)(Buffer.byte)
      r  <- write(bs)
    } yield r

  final def writeChunk(src: Chunk[Byte]): IO[Exception, Long] = writeChunks(List(src))

  private def unwrap(srcs: List[ByteBuffer]): Array[JByteBuffer] = srcs.map(d => d.byteBuffer).toList.toArray
}
