package scalaz.nio.channels

import java.net.{ SocketAddress, SocketOption }
import java.nio.channels.{
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel
}

import scalaz.Maybe
import scalaz.nio.channels.IOAsyncUtil._
import scalaz.zio.IO

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel) {

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections.
   */
  final def bind(address: SocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address)).void

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  // TODO wrap `SocketAddress`
  final def bind(address: SocketAddress, backlog: Int): IO[Exception, Unit] =
    IO.syncException(channel.bind(address, backlog)).void

  // TODO wrap `SocketOption[T]?`
  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name, value)).void

  /**
   * Accepts a connection.
   */
  final def accept: IO[Exception, AsynchronousSocketChannel] =
    wrap[Unit, JAsynchronousSocketChannel](h => channel.accept((), h))
      .map(AsynchronousSocketChannel(_))

  /**
   * Accepts a connection.
   */
  final def accept[A](attachment: A): IO[Exception, AsynchronousSocketChannel] =
    wrap[A, JAsynchronousSocketChannel](h => channel.accept(attachment, h))
      .map(AsynchronousSocketChannel(_))

  /**
   * The `SocketAddress` that the socket is bound to,
   * or the `SocketAddress` representing the loopback address if
   * denied by the security manager, or `Maybe.empty` if the
   * channel's socket is not bound.
   */
  final def localAddress: IO[Exception, Maybe[SocketAddress]] =
    IO.syncException(Maybe.fromNullable(channel.getLocalAddress))

  /**
   * Closes this channel.
   */
  final def close: IO[Exception, Unit] =
    IO.syncException(channel.close())

}

object AsynchronousServerSocketChannel {

  def apply(): IO[Exception, AsynchronousServerSocketChannel] =
    IO.syncException(JAsynchronousServerSocketChannel.open())
      .map(new AsynchronousServerSocketChannel(_))

  def apply(
    channelGroup: AsynchronousChannelGroup
  ): IO[Exception, AsynchronousServerSocketChannel] =
    IO.syncException(
        JAsynchronousServerSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousServerSocketChannel(_))
}
