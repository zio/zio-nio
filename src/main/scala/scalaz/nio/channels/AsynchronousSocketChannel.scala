package scalaz.nio.channels

import java.lang.{ Integer => JInteger, Long => JLong, Void => JVoid }
import java.net.{ SocketAddress, SocketOption }
import java.nio.channels.{ AsynchronousSocketChannel => JAsynchronousSocketChannel }

import scalaz.IList
import scalaz.nio.ByteBuffer
import scalaz.nio.channels.IOAsyncUtil._
import scalaz.zio.IO

import scala.concurrent.duration.Duration

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  final def bind(address: SocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address)).void

  // TODO wrap `SocketOption[T]?`
  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name, value)).void

  final def shutdownInput: IO[Exception, Unit] =
    IO.syncException(channel.shutdownInput()).void

  final def shutdownOutput: IO[Exception, Unit] =
    IO.syncException(channel.shutdownOutput()).void

  final def remoteAddress: IO[Exception, SocketAddress] =
    IO.syncException(channel.getRemoteAddress)

  final def localAddress: IO[Exception, SocketAddress] =
    IO.syncException(channel.getLocalAddress)

  final def connect[A](attachment: A, socketAddress: SocketAddress): IO[Exception, Unit] =
    wrap[A, JVoid](h => channel.connect(socketAddress, attachment, h)).void

  final def connect(socketAddress: SocketAddress): IO[Exception, Unit] =
    wrap[Unit, JVoid](h => channel.connect(socketAddress, (), h)).void

  def read[A](dst: ByteBuffer, timeout: Duration, attachment: A): IO[Exception, Int] =
    wrap[A, JInteger] { h =>
      channel.read(dst.buffer, timeout.length, timeout.unit, attachment, h)
    }.map(_.toInt)

  def read[A](
    dsts: IList[ByteBuffer],
    offset: Int,
    length: Int,
    timeout: Duration,
    attachment: A
  ): IO[Exception, Long] =
    wrap[A, JLong](
      h =>
        channel.read(
          dsts.map(_.buffer).toList.toArray,
          offset,
          length,
          timeout.length,
          timeout.unit,
          attachment,
          h
        )
    ).map(_.toLong)
}

object AsynchronousSocketChannel {

  def apply(): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(JAsynchronousSocketChannel.open())
      .map(new AsynchronousSocketChannel(_))

  def apply(channelGroup: AsynchronousChannelGroup): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(
        JAsynchronousSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousSocketChannel(_))

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)
}
