package zio.nio.channels

import java.nio.{ByteBuffer => JByteBuffer}
import java.nio.channels.{GatheringByteChannel => JGatheringByteChannel}

import zio.{Chunk, IO, UIO}
import zio.nio.Buffer

class GatheringByteChannel(private val channel: JGatheringByteChannel) {

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

  final val close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineToOrDie[Exception]

  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)

  private def unwrap(srcs: List[Buffer[Byte]]): Array[JByteBuffer] =
    srcs.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
}
