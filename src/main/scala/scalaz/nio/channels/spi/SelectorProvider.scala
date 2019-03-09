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
    IO.effect(selectorProvider.openDatagramChannel()).refineOrDie(JustIOException)

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  final def openDatagramChannel(
    family: ProtocolFamily
  ): IO[IOException, JDatagramChannel] = // TODO: wrapper for DatagramChannel
    IO.effect(selectorProvider.openDatagramChannel(family)).refineOrDie(JustIOException)

  final val openPipe: IO[IOException, Pipe] =
    IO.effect(new Pipe(selectorProvider.openPipe())).refineOrDie(JustIOException)

  final val openSelector: IO[IOException, Selector] =
    IO.effect(new Selector(selectorProvider.openSelector())).refineOrDie(JustIOException)

  final val openServerSocketChannel: IO[IOException, ServerSocketChannel] =
    IO.effect(new ServerSocketChannel(selectorProvider.openServerSocketChannel()))
      .refineOrDie(
        JustIOException
      )

  final val openSocketChannel: IO[IOException, SocketChannel] =
    IO.effect(new SocketChannel(selectorProvider.openSocketChannel())).refineOrDie(JustIOException)

  final val inheritedChannel: IO[IOException, Option[JChannel]] = // TODO: wrapper for Channel
    IO.effect(Option(selectorProvider.inheritedChannel())).refineOrDie(JustIOException)
}

object SelectorProvider {

  final val make: IO[Nothing, SelectorProvider] =
    IO.effectTotal(JSelectorProvider.provider()).map(new SelectorProvider(_))

}
