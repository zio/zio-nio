package zio.nio.core.channels.spi

import java.io.IOException
import java.net.ProtocolFamily
import java.nio.channels.{ Channel => JChannel, DatagramChannel => JDatagramChannel }
import java.nio.channels.spi.{ SelectorProvider => JSelectorProvider }

import zio.nio.core.channels.{ Pipe, Selector, ServerSocketChannel, SocketChannel }
import zio.IO

class SelectorProvider(private val selectorProvider: JSelectorProvider) {

  final val openDatagramChannel: IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    IO.effect(selectorProvider.openDatagramChannel()).refineToOrDie[IOException]

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  final def openDatagramChannel(
    family: ProtocolFamily
  ): IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    IO.effect(selectorProvider.openDatagramChannel(family)).refineToOrDie[IOException]

  final val openPipe: IO[IOException, Pipe] =
    IO.effect(new Pipe(selectorProvider.openPipe())).refineToOrDie[IOException]

  final val openSelector: IO[IOException, Selector] =
    IO.effect(new Selector(selectorProvider.openSelector())).refineToOrDie[IOException]

  final val openServerSocketChannel: IO[IOException, ServerSocketChannel] =
    ServerSocketChannel.fromJava(selectorProvider.openServerSocketChannel())

  final val openSocketChannel: IO[IOException, SocketChannel] =
    IO.effect(new SocketChannel(selectorProvider.openSocketChannel())).refineToOrDie[IOException]

  final val inheritedChannel: IO[IOException, Option[JChannel]] = // TODO: wrapper for Channel
    IO.effect(Option(selectorProvider.inheritedChannel())).refineToOrDie[IOException]
}

object SelectorProvider {

  final val make: IO[Nothing, SelectorProvider] =
    IO.effectTotal(JSelectorProvider.provider()).map(new SelectorProvider(_))
}
