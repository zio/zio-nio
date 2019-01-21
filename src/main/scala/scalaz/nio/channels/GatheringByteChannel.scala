package scalaz.nio.channels

import java.nio.channels.{ GatheringByteChannel => JGatheringByteChannel }

import scalaz.nio.ByteBuffer
import scalaz.zio.IO

class GatheringByteChannel(private val channel: JGatheringByteChannel) {

  def write(srcs: Seq[ByteBuffer], offset: Int, length: Int): IO[Exception, Long] =
    IO.syncException(channel.write(unwrap(srcs), offset, length))

  def write(srcs: Seq[ByteBuffer]): IO[Exception, Long] =
    IO.syncException(channel.write(unwrap(srcs)))

  def close(): IO[Exception, Unit] =
    IO.syncException(channel.close())

  private def unwrap(srcs: Seq[ByteBuffer]) = srcs.map(d => d.buffer).toArray
}
