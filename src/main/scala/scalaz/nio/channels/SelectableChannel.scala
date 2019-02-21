package scalaz.nio.channels

import java.io.IOException
import java.net.{ Socket => JSocket, ServerSocket => JServerSocket }
import java.nio.channels.{
  SelectableChannel => JSelectableChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}

import scalaz.nio.channels.spi.SelectorProvider
import scalaz.nio.io._
import scalaz.nio.{ ByteBuffer, SocketAddress, SocketOption }
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
    IO.syncIOException(new SelectionKey(channel.register(sel.selector, ops, att.orNull)))

  final def register(sel: Selector, ops: Int): IO[IOException, SelectionKey] =
    IO.syncIOException(new SelectionKey(channel.register(sel.selector, ops)))

  final def configureBlocking(block: Boolean): IO[IOException, SelectableChannel] =
    IO.syncIOException(new SelectableChannel(channel.configureBlocking(block)))

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
    IO.syncIOException(channel.bind(local.jSocketAddress)).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name.jSocketOption, value)).void

  final val shutdownInput: IO[IOException, Unit] =
    IO.syncIOException(channel.shutdownInput()).void

  final val shutdownOutput: IO[IOException, Unit] =
    IO.syncIOException(channel.shutdownOutput()).void

  final val socket: IO[Nothing, JSocket] =
    IO.sync(channel.socket())

  final val isConnected: IO[Nothing, Boolean] =
    IO.sync(channel.isConnected)

  final val isConnectionPending: IO[Nothing, Boolean] =
    IO.sync(channel.isConnectionPending)

  final def connect(remote: SocketAddress): IO[IOException, Boolean] =
    IO.syncIOException(channel.connect(remote.jSocketAddress))

  final val finishConnect: IO[IOException, Boolean] =
    IO.syncIOException(channel.finishConnect())

  final val remoteAddress: IO[IOException, SocketAddress] =
    IO.syncIOException(new SocketAddress(channel.getRemoteAddress()))

  final def read(b: ByteBuffer): IO[IOException, Int] =
    IO.syncIOException(channel.read(b.buffer))

  final def write(b: ByteBuffer): IO[Exception, Int] =
    IO.syncIOException(channel.write(b.buffer))

  final val localAddress: IO[IOException, Option[SocketAddress]] =
    IO.syncIOException(Option(channel.getLocalAddress()).map(new SocketAddress(_)))

}

object SocketChannel {

  final val open: IO[IOException, SocketChannel] =
    IO.syncIOException(new SocketChannel(JSocketChannel.open()))

  final def open(remote: SocketAddress): IO[IOException, SocketChannel] =
    IO.syncIOException(new SocketChannel(JSocketChannel.open(remote.jSocketAddress)))

}

class ServerSocketChannel(private val channel: JServerSocketChannel)
    extends SelectableChannel(channel) {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.syncIOException(channel.bind(local.jSocketAddress)).void

  final def bind(local: SocketAddress, backlog: Int): IO[IOException, Unit] =
    IO.syncIOException(channel.bind(local.jSocketAddress, backlog)).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name.jSocketOption, value)).void

  final val socket: IO[Nothing, JServerSocket] =
    IO.sync(channel.socket())

  final def accept: IO[IOException, Option[SocketChannel]] =
    IO.syncIOException(Option(channel.accept()).map(new SocketChannel(_)))

  final val localAddress: IO[IOException, SocketAddress] =
    IO.syncIOException(new SocketAddress(channel.getLocalAddress()))

}

object ServerSocketChannel {

  final val open: IO[IOException, ServerSocketChannel] =
    IO.syncIOException(new ServerSocketChannel(JServerSocketChannel.open()))

}
