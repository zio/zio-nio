package scalaz.nio.channels

import java.nio.ByteBuffer
import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }

import scalaz.nio.Buffer
import scalaz.zio.IO

class ScatteringByteChannel(private val channel: JScatteringByteChannel) {

  def read(dsts: Seq[Buffer[Byte]], offset: Int, length: Int): IO[Exception, Long] =
    IO.syncException(channel.read(unwrap(dsts), offset, length))

  def read(dsts: Seq[Buffer[Byte]]): IO[Exception, Long] =
    IO.syncException(channel.read(unwrap(dsts)))

  private def unwrap(dsts: Seq[Buffer[Byte]]) =
    dsts.map(d => d.buffer.asInstanceOf[ByteBuffer]).toArray
}
