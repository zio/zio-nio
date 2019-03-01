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
    IO.syncCatch(selectorProvider.openDatagramChannel())(JustIOException)

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  final def openDatagramChannel(
    family: ProtocolFamily
  ): IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    IO.syncCatch(selectorProvider.openDatagramChannel(family))(JustIOException)

  final val openPipe: IO[IOException, Pipe] =
    IO.syncCatch(new Pipe(selectorProvider.openPipe()))(JustIOException)

  final val openSelector: IO[IOException, Selector] =
    IO.syncCatch(new Selector(selectorProvider.openSelector()))(JustIOException)

  final val openServerSocketChannel: IO[IOException, ServerSocketChannel] =
    IO.syncCatch(new ServerSocketChannel(selectorProvider.openServerSocketChannel()))(
      JustIOException
    )

  final val openSocketChannel: IO[IOException, SocketChannel] =
    IO.syncCatch(new SocketChannel(selectorProvider.openSocketChannel()))(JustIOException)

  final val inheritedChannel: IO[IOException, Option[JChannel]] = // TODO: wrapper for Channel
    IO.syncCatch(Option(selectorProvider.inheritedChannel()))(JustIOException)
}

object SelectorProvider {

  final val make: IO[Nothing, SelectorProvider] =
    IO.sync(JSelectorProvider.provider()).map(new SelectorProvider(_))

}
