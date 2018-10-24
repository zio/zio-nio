package scalaz.nio.channels

import java.net.InetSocketAddress
import java.nio.channels.{
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel
}

import scalaz.nio.channels.IOAsyncUtil._
import scalaz.zio.IO

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel) {

  /**
   * Binds the channel's socket to a local address and configures the socket to listen for connections.
   */
  final def bind(address: InetSocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address)).void

  /**
   * Accepts a connection.
   */
  final def accept: IO[Exception, AsynchronousSocketChannel] =
    wrap[JAsynchronousSocketChannel](h => channel.accept((), h))
      .map(AsynchronousSocketChannel(_))

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
