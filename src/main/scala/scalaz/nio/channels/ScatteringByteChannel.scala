package scalaz.nio.channels

import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }

import scalaz.nio.Buffer
import scalaz.zio.{ Chunk, IO, JustExceptions, UIO }

class ScatteringByteChannel(private val channel: JScatteringByteChannel) {

  final private[nio] def readBuffer(
    dsts: List[Buffer[Byte]],
    offset: Int,
    length: Int
  ): IO[Exception, Long] =
    IO.effect(channel.read(unwrap(dsts), offset, length)).refineOrDie(JustExceptions)

  final def read(dsts: List[Chunk[Byte]], offset: Int, length: Int): IO[Exception, Long] =
    for {
      bs <- IO.collectAll(dsts.map(Buffer.byte(_)))
      r  <- readBuffer(bs, offset, length)
    } yield r

  final private[nio] def readBuffer(dsts: List[Buffer[Byte]]): IO[Exception, Long] =
    IO.effect(channel.read(unwrap(dsts))).refineOrDie(JustExceptions)

  final def read(dsts: List[Chunk[Byte]]): IO[Exception, Long] =
    for {
      bs <- IO.collectAll(dsts.map(Buffer.byte(_)))
      r  <- readBuffer(bs)
    } yield r

  final val close: IO[Exception, Unit] =
    IO.effect(channel.close).refineOrDie(JustExceptions)

  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)

  private def unwrap(dsts: List[Buffer[Byte]]): Array[JByteBuffer] =
    dsts.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
}
