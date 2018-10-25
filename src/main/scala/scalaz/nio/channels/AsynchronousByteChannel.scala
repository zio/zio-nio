package scalaz.nio.channels

import java.lang.{ Integer => JInteger }
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
    wrap[Unit, JInteger](h => channel.read(b.buffer, (), h)).map(_.toInt)

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final def read[A](b: ByteBuffer, attachment: A): IO[Exception, Int] =
    wrap[A, JInteger](h => channel.read(b.buffer, attachment, h)).map(_.toInt)

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write(b: ByteBuffer): IO[Exception, Int] =
    wrap[Unit, JInteger](h => channel.write(b.buffer, (), h)).map(_.toInt)

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write[A](b: ByteBuffer, attachment: A): IO[Exception, Int] =
    wrap[A, JInteger](h => channel.write(b.buffer, attachment, h)).map(_.toInt)

  /**
   * Closes this channel.
   */
  final def close: IO[Exception, Unit] =
    IO.syncException(channel.close())

}
