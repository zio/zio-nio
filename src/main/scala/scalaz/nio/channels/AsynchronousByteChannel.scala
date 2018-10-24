package scalaz.nio.channels

import java.nio.channels.{ AsynchronousByteChannel => JAsynchronousByteChannel }

import scalaz.nio.ByteBuffer
import scalaz.nio.channels.IOAsyncUtil._
import scalaz.zio.IO

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final def read(b: ByteBuffer): IO[Exception, Int] =
    wrap[Integer] { h =>
      channel.read(b.buffer, (), h)
    }.map(_.toInt)

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write(b: ByteBuffer): IO[Exception, Int] =
    wrap[Integer] { h =>
      channel.write(b.buffer, (), h)
    }.map(_.toInt)

}
