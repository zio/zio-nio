package zio.nio

package channels

import java.io.IOException
import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }
import java.nio.{ ByteBuffer => JByteBuffer }

import core.{ Buffer, ByteBuffer, RichLong }
import zio.{ Chunk, IO, UIO }

trait ScatteringByteChannel extends Channel {
  override protected[channels] val channel: JScatteringByteChannel

  final def read(dsts: List[ByteBuffer]): IO[IOException, Option[Long]] =
    IO.effect(channel.read(unwrap(dsts)).eofCheck).refineToOrDie[IOException]

  final def read(dst: ByteBuffer): IO[IOException, Option[Long]] = read(List(dst))

  final def readChunk(capacity: Int): IO[IOException, Chunk[Byte]] =
    for {
      buffer    <- Buffer.byte(capacity)
      readCount <- read(buffer)
      _         <- buffer.flip
      chunk     <- readCount.map(count => buffer.getChunk(count.toInt)).getOrElse(UIO.succeed(Chunk.empty))
    } yield chunk

  private def unwrap(dsts: List[ByteBuffer]): Array[JByteBuffer] =
    dsts.map(d => d.byteBuffer).toArray
}
