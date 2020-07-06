package zio.nio.core.channels.spi

import java.io.IOException
import java.net.ProtocolFamily
import java.nio.{ channels => jc }
import java.nio.channels.spi.{ SelectorProvider => JSelectorProvider }

import zio.nio.core.channels._
import zio.IO

final class SelectorProvider(private val selectorProvider: JSelectorProvider) {

  val openDatagramChannel: IO[IOException, DatagramChannel.Blocking] =
    IO.effect(DatagramChannel.Blocking.fromJava(selectorProvider.openDatagramChannel())).refineToOrDie[IOException]

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  def openDatagramChannel(
    family: ProtocolFamily
  ): IO[IOException, DatagramChannel.Blocking] =
    IO.effect(DatagramChannel.Blocking.fromJava(selectorProvider.openDatagramChannel(family)))
      .refineToOrDie[IOException]

  val openPipe: IO[IOException, Pipe] =
    IO.effect(Pipe.fromJava(selectorProvider.openPipe())).refineToOrDie[IOException]

  val openSelector: IO[IOException, Selector] =
    IO.effect(new Selector(selectorProvider.openSelector())).refineToOrDie[IOException]

  val openServerSocketChannel: IO[IOException, ServerSocketChannel.Blocking] =
    IO.effect(ServerSocketChannel.Blocking.fromJava(selectorProvider.openServerSocketChannel()))
      .refineToOrDie[IOException]

  val openSocketChannel: IO[IOException, SocketChannel.Blocking] =
    IO.effect(SocketChannel.Blocking.fromJava(selectorProvider.openSocketChannel())).refineToOrDie[IOException]

  val inheritedChannel: IO[IOException, Option[Channel]] =
    IO.effect(Option(selectorProvider.inheritedChannel()))
      .map {
        _.collect {
          case c: jc.SocketChannel       => SocketChannel.Blocking.fromJava(c)
          case c: jc.ServerSocketChannel => ServerSocketChannel.Blocking.fromJava(c)
          case c: jc.DatagramChannel     => DatagramChannel.Blocking.fromJava(c)
          case c: jc.FileChannel         => FileChannel.fromJava(c)
        }
      }
      .refineToOrDie[IOException]
}

object SelectorProvider {

  final def default: IO[Nothing, SelectorProvider] =
    IO.effectTotal(JSelectorProvider.provider()).map(new SelectorProvider(_))

}
