package scalaz.nio.channels

import java.io.IOException
import java.net.{ Socket => JSocket, ServerSocket => JServerSocket }
import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{
  SelectableChannel => JSelectableChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}

import scalaz.nio.channels.spi.SelectorProvider
import scalaz.nio.io._
import scalaz.nio.{ Buffer, SocketAddress, SocketOption }
import scalaz.zio.{ IO, JustExceptions, UIO }

class SelectableChannel(private val channel: JSelectableChannel) {

  final val provider: UIO[SelectorProvider] =
    IO.effectTotal(new SelectorProvider(channel.provider()))

  final val validOps: UIO[Int] =
    IO.effectTotal(channel.validOps())

  final val isRegistered: UIO[Boolean] =
    IO.effectTotal(channel.isRegistered())

  final def keyFor(sel: Selector): UIO[Option[SelectionKey]] =
    IO.effectTotal(Option(channel.keyFor(sel.selector)).map(new SelectionKey(_)))

  final def register(sel: Selector, ops: Int, att: Option[AnyRef]): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, ops, att.orNull)))
      .refineOrDie(JustIOException)

  final def register(sel: Selector, ops: Int): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, ops))).refineOrDie(JustIOException)

  final def configureBlocking(block: Boolean): IO[IOException, SelectableChannel] =
    IO.effect(new SelectableChannel(channel.configureBlocking(block))).refineOrDie(JustIOException)

  final val isBlocking: UIO[Boolean] =
    IO.effectTotal(channel.isBlocking())

  final val blockingLock: UIO[AnyRef] =
    IO.effectTotal(channel.blockingLock())

  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)

  final def close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineOrDie {
      case e: Exception => e
    }

}

class SocketChannel(private val channel: JSocketChannel) extends SelectableChannel(channel) {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress)).refineOrDie(JustIOException).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.effect(channel.setOption(name.jSocketOption, value)).refineOrDie(JustExceptions).void

  final val shutdownInput: IO[IOException, Unit] =
    IO.effect(channel.shutdownInput()).refineOrDie(JustIOException).void

  final val shutdownOutput: IO[IOException, Unit] =
    IO.effect(channel.shutdownOutput()).refineOrDie(JustIOException).void

  final val socket: UIO[JSocket] =
    IO.effectTotal(channel.socket())

  final val isConnected: UIO[Boolean] =
    IO.effectTotal(channel.isConnected)

  final val isConnectionPending: UIO[Boolean] =
    IO.effectTotal(channel.isConnectionPending)

  final def connect(remote: SocketAddress): IO[IOException, Boolean] =
    IO.effect(channel.connect(remote.jSocketAddress)).refineOrDie(JustIOException)

  final val finishConnect: IO[IOException, Boolean] =
    IO.effect(channel.finishConnect()).refineOrDie(JustIOException)

  final val remoteAddress: IO[IOException, SocketAddress] =
    IO.effect(new SocketAddress(channel.getRemoteAddress())).refineOrDie(JustIOException)

  final def read(b: Buffer[Byte]): IO[IOException, Int] =
    IO.effect(channel.read(b.buffer.asInstanceOf[JByteBuffer])).refineOrDie(JustIOException)

  final def write(b: Buffer[Byte]): IO[Exception, Int] =
    IO.effect(channel.write(b.buffer.asInstanceOf[JByteBuffer])).refineOrDie(JustIOException)

  final val localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getLocalAddress()).map(new SocketAddress(_)))
      .refineOrDie(JustIOException)

}

object SocketChannel {

  final val open: IO[IOException, SocketChannel] =
    IO.effect(new SocketChannel(JSocketChannel.open())).refineOrDie(JustIOException)

  final def open(remote: SocketAddress): IO[IOException, SocketChannel] =
    IO.effect(new SocketChannel(JSocketChannel.open(remote.jSocketAddress)))
      .refineOrDie(JustIOException)

}

class ServerSocketChannel(private val channel: JServerSocketChannel)
    extends SelectableChannel(channel) {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress)).refineOrDie(JustIOException).void

  final def bind(local: SocketAddress, backlog: Int): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress, backlog)).refineOrDie(JustIOException).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.effect(channel.setOption(name.jSocketOption, value)).refineOrDie(JustExceptions).void

  final val socket: UIO[JServerSocket] =
    IO.effectTotal(channel.socket())

  final def accept: IO[IOException, Option[SocketChannel]] =
    IO.effect(Option(channel.accept()).map(new SocketChannel(_))).refineOrDie(JustIOException)

  final val localAddress: IO[IOException, SocketAddress] =
    IO.effect(new SocketAddress(channel.getLocalAddress())).refineOrDie(JustIOException)

}

object ServerSocketChannel {

  final val open: IO[IOException, ServerSocketChannel] =
    IO.effect(new ServerSocketChannel(JServerSocketChannel.open())).refineOrDie(JustIOException)

}
