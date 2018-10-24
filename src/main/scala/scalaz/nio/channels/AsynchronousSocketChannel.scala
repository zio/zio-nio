package scalaz.nio.channels

import java.net.InetSocketAddress
import java.nio.channels.{ AsynchronousSocketChannel => JAsynchronousSocketChannel }

import scalaz.nio.channels.IOAsyncUtil._
import scalaz.zio.IO

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  final def connect(socketAddress: InetSocketAddress): IO[Exception, Unit] =
    wrap[Void](h => channel.connect(socketAddress, (), h)).void

}

object AsynchronousSocketChannel {

  def apply(): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(JAsynchronousSocketChannel.open())
      .map(new AsynchronousSocketChannel(_))

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)

  def apply(channelGroup: AsynchronousChannelGroup): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(
        JAsynchronousSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousSocketChannel(_))
}
