package zio.nio.channels

import java.nio.channels.{ GatheringByteChannel => JGatheringByteChannel }
import java.nio.{ ByteBuffer => JByteBuffer }

import zio.nio.Buffer
import zio.{ Chunk, IO }

trait GatheringByteChannel extends Channel {

  override protected[channels] val channel: JGatheringByteChannel

  final private[nio] def writeBuffer(
    srcs: List[Buffer[Byte]],
    offset: Int,
    length: Int
  ): IO[Exception, Long] =
    IO.effect(channel.write(unwrap(srcs), offset, length)).refineToOrDie[Exception]

  final def write(srcs: List[Chunk[Byte]], offset: Int, length: Int): IO[Exception, Long] =
    for {
      bs <- IO.collectAll(srcs.map(Buffer.byte(_)))
      r  <- writeBuffer(bs, offset, length)
    } yield r

  final private[nio] def writeBuffer(srcs: List[Buffer[Byte]]): IO[Exception, Long] =
    IO.effect(channel.write(unwrap(srcs))).refineToOrDie[Exception]

  final def write(srcs: List[Chunk[Byte]]): IO[Exception, Long] =
    for {
      bs <- IO.collectAll(srcs.map(Buffer.byte(_)))
      r  <- writeBuffer(bs)
    } yield r

  private def unwrap(srcs: List[Buffer[Byte]]): Array[JByteBuffer] =
    srcs.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
}
