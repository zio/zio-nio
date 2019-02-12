package scalaz.nio.channels

import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }

import scalaz.nio.ByteBuffer
import scalaz.zio.IO

class ScatteringByteChannel(private val channel: JScatteringByteChannel) {

  def read(dsts: Seq[ByteBuffer], offset: Int, length: Int): IO[Exception, Long] =
    IO.syncException(channel.read(unwrap(dsts), offset, length))

  def read(dsts: Seq[ByteBuffer]): IO[Exception, Long] =
    IO.syncException(channel.read(unwrap(dsts)))

  private def unwrap(dsts: Seq[ByteBuffer]) = dsts.map(d => d.buffer).toArray
}
