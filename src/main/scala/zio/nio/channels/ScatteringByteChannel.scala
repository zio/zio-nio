package zio.nio

package channels

import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }
import java.nio.{ ByteBuffer => JByteBuffer }

import zio.{ Chunk, IO }

trait ScatteringByteChannel extends Channel {
  override protected[channels] val channel: JScatteringByteChannel

  final def readBuffer(dsts: List[Buffer[Byte]]): IO[Exception, Option[Long]] =
    IO.effect(channel.read(unwrap(dsts)).eofCheck).refineToOrDie[Exception]

  final def readBuffer(dst: Buffer[Byte]): IO[Exception, Option[Long]] = readBuffer(List(dst))

  final def read(capacity: Int): IO[Exception, Option[Chunk[Byte]]] = {
    require(capacity > 0)
    for {
      buffer    <- Buffer.byte(capacity)
      readCount <- readBuffer(buffer)
      _         <- buffer.flip
      chunk     <- readCount.map(count => buffer.getChunk(count.toInt).map(Some.apply)).getOrElse(IO.succeed(None))
    } yield chunk
  }

  private def unwrap(dsts: List[Buffer[Byte]]): Array[JByteBuffer] =
    dsts.map(d => d.buffer.asInstanceOf[JByteBuffer]).toList.toArray
}
