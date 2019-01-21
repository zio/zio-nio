package scalaz.nio.channels.spi

import java.net.ProtocolFamily
import java.nio.channels.{
  Channel => JChannel,
  DatagramChannel => JDatagramChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}
import java.nio.channels.spi.{SelectorProvider => JSelectorProvider}

import scalaz.nio.channels.{Pipe, Selector}
import scalaz.zio.IO

class SelectorProvider(private val selectorProvider: JSelectorProvider) {

  def openDatagramChannel: IO[Exception, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    IO.syncException(selectorProvider.openDatagramChannel())

  def openDatagramChannel(family: ProtocolFamily): IO[Exception, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    IO.syncException(selectorProvider.openDatagramChannel(family))

  def openPipe(): IO[Exception, Pipe] =
    IO.syncException(selectorProvider.openPipe()).map(new Pipe(_))

  def openSelector(): IO[Exception, Selector] =
    IO.syncException(selectorProvider.openSelector()).map(new Selector(_))

  def openServerSocketChannel(): IO[Exception, JServerSocketChannel] = // TODO: wrapper for ServerSocketChannel
    IO.syncException(selectorProvider.openServerSocketChannel())

  def openSocketChannel(): IO[Exception, JSocketChannel] = // TODO: wrapper for SocketChannel
    IO.syncException(selectorProvider.openSocketChannel())

  def inheritedChannel(): IO[Exception, Option[JChannel]] = // TODO: wrapper for Channel
    IO.syncException(selectorProvider.inheritedChannel()).map(Option(_))
}

object SelectorProvider {

  def apply(): IO[Nothing, SelectorProvider] =
    IO.sync(JSelectorProvider.provider()).map(new SelectorProvider(_))

}
