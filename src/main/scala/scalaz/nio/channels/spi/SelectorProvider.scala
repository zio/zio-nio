package scalaz.nio.channels.spi

import java.io.IOException
import java.net.ProtocolFamily
import java.nio.channels.{ Channel => JChannel, DatagramChannel => JDatagramChannel }
import java.nio.channels.spi.{ SelectorProvider => JSelectorProvider }

import scalaz.nio.channels.{ Pipe, Selector, ServerSocketChannel, SocketChannel }
import scalaz.nio.io._
import scalaz.zio.IO

class SelectorProvider(private val selectorProvider: JSelectorProvider) {

  final val openDatagramChannel
    : IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    IO.syncIOException(selectorProvider.openDatagramChannel())

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  final def openDatagramChannel(
    family: ProtocolFamily
  ): IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    IO.syncIOException(selectorProvider.openDatagramChannel(family))

  final val openPipe: IO[IOException, Pipe] =
    IO.syncIOException(selectorProvider.openPipe()).map(new Pipe(_))

  final val openSelector: IO[IOException, Selector] =
    IO.syncIOException(selectorProvider.openSelector()).map(new Selector(_))

  final val openServerSocketChannel: IO[IOException, ServerSocketChannel] =
    IO.syncIOException(selectorProvider.openServerSocketChannel()).map(new ServerSocketChannel(_))

  final val openSocketChannel: IO[IOException, SocketChannel] =
    IO.syncIOException(selectorProvider.openSocketChannel()).map(new SocketChannel(_))

  final val inheritedChannel: IO[IOException, Option[JChannel]] = // TODO: wrapper for Channel
    IO.syncIOException(selectorProvider.inheritedChannel()).map(Option(_))
}

object SelectorProvider {

  final val make: IO[Nothing, SelectorProvider] =
    IO.sync(JSelectorProvider.provider()).map(new SelectorProvider(_))

}
