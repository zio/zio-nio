package scalaz.nio.channels

import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }

import scalaz._
import Scalaz._
import scalaz.nio.Buffer
import scalaz.zio.{ Chunk, IO }
import scalaz.zio.interop.scalaz72._

class ScatteringByteChannel(private val channel: JScatteringByteChannel) {

  final private[nio] def readBuffer(
    dsts: IList[Buffer[Byte]],
    offset: Int,
    length: Int
  ): IO[Exception, Long] =
    IO.syncException(channel.read(unwrap(dsts), offset, length))

  final def read(dsts: IList[Chunk[Byte]], offset: Int, length: Int): IO[Exception, Long] =
    for {
      bs <- dsts.map(Buffer.byte(_)).sequence
      r  <- readBuffer(bs, offset, length)
    } yield r

  final private[nio] def readBuffer(dsts: IList[Buffer[Byte]]): IO[Exception, Long] =
    IO.syncException(channel.read(unwrap(dsts)))

  final def read(dsts: IList[Chunk[Byte]]): IO[Exception, Long] =
    for {
      bs <- dsts.map(Buffer.byte(_)).sequence
      r  <- readBuffer(bs)
    } yield r

  final def close(): IO[Exception, Unit] =
    IO.syncException(channel.close)

  final def isOpen(): IO[Exception, Boolean] =
    IO.syncException(channel.isOpen)

  private def unwrap(dsts: IList[Buffer[Byte]]): Array[JByteBuffer] =
    dsts.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
}
