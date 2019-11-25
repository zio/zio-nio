package zio.nio.core.channels

import java.nio.channels.{ GatheringByteChannel => JGatheringByteChannel }
import java.nio.{ ByteBuffer => JByteBuffer }

import zio.nio.core.Buffer
import zio.{ Chunk, IO }

trait GatheringByteChannel extends Channel {
  override protected[channels] val channel: JGatheringByteChannel

  final def writeBuffer(srcs: List[Buffer[Byte]]): IO[Exception, Long] =
    IO.effect(channel.write(unwrap(srcs))).refineToOrDie[Exception]

  final def writeBuffer(src: Buffer[Byte]): IO[Exception, Long] = writeBuffer(List(src))

  final def write(srcs: List[Chunk[Byte]]): IO[Exception, Long] =
    for {
      bs <- IO.collectAll(srcs.map(Buffer.byte(_)))
      r  <- writeBuffer(bs)
    } yield r

  final def write(src: Chunk[Byte]): IO[Exception, Long] = write(List(src))

  private def unwrap(srcs: List[Buffer[Byte]]): Array[JByteBuffer] =
    srcs.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
}
