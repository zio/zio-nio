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
import scalaz.zio.IO

class SelectableChannel(private val channel: JSelectableChannel) {

  final val provider: IO[Nothing, SelectorProvider] =
    IO.sync(new SelectorProvider(channel.provider()))

  final val validOps: IO[Nothing, Int] =
    IO.sync(channel.validOps())

  final val isRegistered: IO[Nothing, Boolean] =
    IO.sync(channel.isRegistered())

  final def keyFor(sel: Selector): IO[Nothing, Option[SelectionKey]] =
    IO.sync(Option(channel.keyFor(sel.selector)).map(new SelectionKey(_)))

  final def register(sel: Selector, ops: Int, att: Option[AnyRef]): IO[IOException, SelectionKey] =
    IO.syncCatch(new SelectionKey(channel.register(sel.selector, ops, att.orNull)))(JustIOException)

  final def register(sel: Selector, ops: Int): IO[IOException, SelectionKey] =
    IO.syncCatch(new SelectionKey(channel.register(sel.selector, ops)))(JustIOException)

  final def configureBlocking(block: Boolean): IO[IOException, SelectableChannel] =
    IO.syncCatch(new SelectableChannel(channel.configureBlocking(block)))(JustIOException)

  final val isBlocking: IO[Nothing, Boolean] =
    IO.sync(channel.isBlocking())

  final val blockingLock: IO[Nothing, AnyRef] =
    IO.sync(channel.blockingLock())

  final val isOpen: IO[Nothing, Boolean] =
    IO.sync(channel.isOpen())

  final def close: IO[Exception, Unit] =
    IO.syncException(channel.close())

}

class SocketChannel(private val channel: JSocketChannel) extends SelectableChannel(channel) {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.syncCatch(channel.bind(local.jSocketAddress))(JustIOException).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name.jSocketOption, value)).void

  final val shutdownInput: IO[IOException, Unit] =
    IO.syncCatch(channel.shutdownInput())(JustIOException).void

  final val shutdownOutput: IO[IOException, Unit] =
    IO.syncCatch(channel.shutdownOutput())(JustIOException).void

  final val socket: IO[Nothing, JSocket] =
    IO.sync(channel.socket())

  final val isConnected: IO[Nothing, Boolean] =
    IO.sync(channel.isConnected)

  final val isConnectionPending: IO[Nothing, Boolean] =
    IO.sync(channel.isConnectionPending)

  final def connect(remote: SocketAddress): IO[IOException, Boolean] =
    IO.syncCatch(channel.connect(remote.jSocketAddress))(JustIOException)

  final val finishConnect: IO[IOException, Boolean] =
    IO.syncCatch(channel.finishConnect())(JustIOException)

  final val remoteAddress: IO[IOException, SocketAddress] =
    IO.syncCatch(new SocketAddress(channel.getRemoteAddress()))(JustIOException)

  final def read(b: Buffer[Byte]): IO[IOException, Int] =
    IO.syncCatch(channel.read(b.buffer.asInstanceOf[JByteBuffer]))(JustIOException)

  final def write(b: Buffer[Byte]): IO[Exception, Int] =
    IO.syncCatch(channel.write(b.buffer.asInstanceOf[JByteBuffer]))(JustIOException)

  final val localAddress: IO[IOException, Option[SocketAddress]] =
    IO.syncCatch(Option(channel.getLocalAddress()).map(new SocketAddress(_)))(JustIOException)

}

object SocketChannel {

  final val open: IO[IOException, SocketChannel] =
    IO.syncCatch(new SocketChannel(JSocketChannel.open()))(JustIOException)

  final def open(remote: SocketAddress): IO[IOException, SocketChannel] =
    IO.syncCatch(new SocketChannel(JSocketChannel.open(remote.jSocketAddress)))(JustIOException)

}

class ServerSocketChannel(private val channel: JServerSocketChannel)
    extends SelectableChannel(channel) {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.syncCatch(channel.bind(local.jSocketAddress))(JustIOException).void

  final def bind(local: SocketAddress, backlog: Int): IO[IOException, Unit] =
    IO.syncCatch(channel.bind(local.jSocketAddress, backlog))(JustIOException).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name.jSocketOption, value)).void

  final val socket: IO[Nothing, JServerSocket] =
    IO.sync(channel.socket())

  final def accept: IO[IOException, Option[SocketChannel]] =
    IO.syncCatch(Option(channel.accept()).map(new SocketChannel(_)))(JustIOException)

  final val localAddress: IO[IOException, SocketAddress] =
    IO.syncCatch(new SocketAddress(channel.getLocalAddress()))(JustIOException)

}

object ServerSocketChannel {

  final val open: IO[IOException, ServerSocketChannel] =
    IO.syncCatch(new ServerSocketChannel(JServerSocketChannel.open()))(JustIOException)

}
