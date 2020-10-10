package zio.nio

package channels

import java.io.IOException
import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }

import zio.{ Chunk, IO }
import zio.nio.core.{ Buffer, ByteBuffer, eofCheck }

trait ScatteringByteChannel extends Channel {
  override protected[channels] val channel: JScatteringByteChannel

  final def read(dsts: List[ByteBuffer]): IO[IOException, Long] =
    IO.effect(channel.read(unwrap(dsts))).refineToOrDie[IOException].flatMap(eofCheck)

  final def read(dst: ByteBuffer): IO[IOException, Long] = read(List(dst))

  final def readChunk(capacity: Int): IO[IOException, Chunk[Byte]] =
    for {
      buffer <- Buffer.byte(capacity)
      _      <- read(buffer)
      _      <- buffer.flip
      chunk  <- buffer.getChunk()
    } yield chunk

  private def unwrap(dsts: List[ByteBuffer]): Array[JByteBuffer] = dsts.map(d => d.byteBuffer).toArray
}
