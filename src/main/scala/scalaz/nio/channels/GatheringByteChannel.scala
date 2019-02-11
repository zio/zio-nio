package scalaz.nio.channels

import java.nio.ByteBuffer
import java.nio.channels.{ GatheringByteChannel => JGatheringByteChannel }
import scalaz.nio.{ Buffer }
import scalaz.zio.IO

class GatheringByteChannel(private val channel: JGatheringByteChannel) {

  def write(srcs: Seq[Buffer[Byte]], offset: Int, length: Int): IO[Exception, Long] =
    IO.syncException(channel.write(unwrap(srcs), offset, length))

  def write(srcs: Seq[Buffer[Byte]]): IO[Exception, Long] =
    IO.syncException(channel.write(unwrap(srcs)))

  def close(): IO[Exception, Unit] =
    IO.syncException(channel.close())

  private def unwrap(srcs: Seq[Buffer[Byte]]) =
    srcs.map(d => d.buffer.asInstanceOf[ByteBuffer]).toArray
}
