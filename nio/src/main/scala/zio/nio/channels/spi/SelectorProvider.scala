package zio.nio.channels.spi

import zio.nio.channels._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Trace, ZIO}

import java.io.IOException
import java.net.ProtocolFamily
import java.nio.channels.spi.{SelectorProvider => JSelectorProvider}
import java.nio.{channels => jc}

final class SelectorProvider(private val selectorProvider: JSelectorProvider) {

  def openDatagramChannel(implicit trace: Trace): IO[IOException, DatagramChannel] =
    ZIO.attempt(DatagramChannel.fromJava(selectorProvider.openDatagramChannel())).refineToOrDie[IOException]

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  def openDatagramChannel(
    family: ProtocolFamily
  )(implicit trace: Trace): IO[IOException, DatagramChannel] =
    ZIO.attempt(DatagramChannel.fromJava(selectorProvider.openDatagramChannel(family))).refineToOrDie[IOException]

  def openPipe(implicit trace: Trace): IO[IOException, Pipe] =
    ZIO.attempt(Pipe.fromJava(selectorProvider.openPipe())).refineToOrDie[IOException]

  def openSelector(implicit trace: Trace): IO[IOException, Selector] =
    ZIO.attempt(new Selector(selectorProvider.openSelector())).refineToOrDie[IOException]

  def openServerSocketChannel(implicit trace: Trace): IO[IOException, ServerSocketChannel] =
    ZIO.attempt(ServerSocketChannel.fromJava(selectorProvider.openServerSocketChannel())).refineToOrDie[IOException]

  def openSocketChannel(implicit trace: Trace): IO[IOException, SocketChannel] =
    ZIO.attempt(new SocketChannel(selectorProvider.openSocketChannel())).refineToOrDie[IOException]

  def inheritedChannel(implicit trace: Trace): IO[IOException, Option[Channel]] =
    ZIO
      .attempt(Option(selectorProvider.inheritedChannel()))
      .map {
        _.collect {
          case c: jc.SocketChannel       => SocketChannel.fromJava(c)
          case c: jc.ServerSocketChannel => ServerSocketChannel.fromJava(c)
          case c: jc.DatagramChannel     => DatagramChannel.fromJava(c)
          case c: jc.FileChannel         => FileChannel.fromJava(c)
        }
      }
      .refineToOrDie[IOException]

}

object SelectorProvider {

  final def default(implicit trace: Trace): IO[Nothing, SelectorProvider] =
    ZIO.succeed(JSelectorProvider.provider()).map(new SelectorProvider(_))

}
