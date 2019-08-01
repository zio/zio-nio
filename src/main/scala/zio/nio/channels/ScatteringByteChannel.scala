package zio.nio.channels

import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }
import java.nio.{ ByteBuffer => JByteBuffer }

import zio.nio.Buffer
import zio.{ Chunk, IO }

trait ScatteringByteChannel extends Channel {

  override protected[channels] val channel: JScatteringByteChannel

  final private[nio] def readBuffer(
    dsts: List[Buffer[Byte]],
    offset: Int,
    length: Int
  ): IO[Exception, Long] =
    IO.effect(channel.read(unwrap(dsts), offset, length)).refineToOrDie[Exception]

  final def read(dsts: List[Chunk[Byte]], offset: Int, length: Int): IO[Exception, Long] =
    for {
      bs <- IO.collectAll(dsts.map(Buffer.byte(_)))
      r  <- readBuffer(bs, offset, length)
    } yield r

  final private[nio] def readBuffer(dsts: List[Buffer[Byte]]): IO[Exception, Long] =
    IO.effect(channel.read(unwrap(dsts))).refineToOrDie[Exception]

  final def read(dsts: List[Chunk[Byte]]): IO[Exception, Long] =
    for {
      bs <- IO.collectAll(dsts.map(Buffer.byte(_)))
      r  <- readBuffer(bs)
    } yield r

  private def unwrap(dsts: List[Buffer[Byte]]): Array[JByteBuffer] =
    dsts.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
}
