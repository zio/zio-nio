package zio.nio.channels.spi

import zio.nio.channels._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, ZTraceElement}

import java.io.IOException
import java.net.ProtocolFamily
import java.nio.channels.spi.{SelectorProvider => JSelectorProvider}
import java.nio.{channels => jc}

final class SelectorProvider(private val selectorProvider: JSelectorProvider) {

  def openDatagramChannel(implicit trace: ZTraceElement): IO[IOException, DatagramChannel] =
    IO.attempt(DatagramChannel.fromJava(selectorProvider.openDatagramChannel())).refineToOrDie[IOException]

  // this can throw UnsupportedOperationException - doesn't seem like a recoverable exception
  def openDatagramChannel(
    family: ProtocolFamily
  )(implicit trace: ZTraceElement): IO[IOException, DatagramChannel] =
    IO.attempt(DatagramChannel.fromJava(selectorProvider.openDatagramChannel(family))).refineToOrDie[IOException]

  def openPipe(implicit trace: ZTraceElement): IO[IOException, Pipe] =
    IO.attempt(Pipe.fromJava(selectorProvider.openPipe())).refineToOrDie[IOException]

  def openSelector(implicit trace: ZTraceElement): IO[IOException, Selector] =
    IO.attempt(new Selector(selectorProvider.openSelector())).refineToOrDie[IOException]

  def openServerSocketChannel(implicit trace: ZTraceElement): IO[IOException, ServerSocketChannel] =
    IO.attempt(ServerSocketChannel.fromJava(selectorProvider.openServerSocketChannel())).refineToOrDie[IOException]

  def openSocketChannel(implicit trace: ZTraceElement): IO[IOException, SocketChannel] =
    IO.attempt(new SocketChannel(selectorProvider.openSocketChannel())).refineToOrDie[IOException]

  def inheritedChannel(implicit trace: ZTraceElement): IO[IOException, Option[Channel]] =
    IO.attempt(Option(selectorProvider.inheritedChannel()))
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

  final def default(implicit trace: ZTraceElement): IO[Nothing, SelectorProvider] =
    IO.succeed(JSelectorProvider.provider()).map(new SelectorProvider(_))

}
