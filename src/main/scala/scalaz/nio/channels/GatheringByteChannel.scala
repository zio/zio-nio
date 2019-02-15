package scalaz.nio.channels

import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{ GatheringByteChannel => JGatheringByteChannel }

import scalaz._
import Scalaz._
import scalaz.nio.{ Buffer }
import scalaz.zio.{ Chunk, IO }
import scalaz.zio.interop.scalaz72._

class GatheringByteChannel(private val channel: JGatheringByteChannel) {

  final private[nio] def writeBuffer(
    srcs: IList[Buffer[Byte]],
    offset: Int,
    length: Int
  ): IO[Exception, Long] =
    IO.syncException(channel.write(unwrap(srcs), offset, length))

  final def write(srcs: IList[Chunk[Byte]], offset: Int, length: Int): IO[Exception, Long] =
    for {
      bs <- srcs.map(Buffer.byte(_)).sequence
      r  <- writeBuffer(bs, offset, length)
    } yield r

  final private[nio] def writeBuffer(srcs: IList[Buffer[Byte]]): IO[Exception, Long] =
    IO.syncException(channel.write(unwrap(srcs)))

  final def write(srcs: IList[Chunk[Byte]]): IO[Exception, Long] =
    for {
      bs <- srcs.map(Buffer.byte(_)).sequence
      r  <- writeBuffer(bs)
    } yield r

  final def close(): IO[Exception, Unit] =
    IO.syncException(channel.close())

  final def isOpen(): IO[Exception, Boolean] =
    IO.syncException(channel.isOpen)

  private def unwrap(srcs: IList[Buffer[Byte]]): Array[JByteBuffer] =
    srcs.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
}
