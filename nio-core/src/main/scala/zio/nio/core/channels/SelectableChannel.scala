package zio.nio.core
package channels

import zio.nio.core.channels.SelectionKey.Operation
import zio.nio.core.channels.spi.SelectorProvider
import zio.{ IO, Managed, UIO }

import java.io.IOException
import java.net.{ ServerSocket => JServerSocket, Socket => JSocket, SocketOption }
import java.nio.channels.{
  SelectableChannel => JSelectableChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}

/**
 * A channel that can be multiplexed via a [[zio.nio.core.channels.Selector]].
 */
trait SelectableChannel extends Channel {
  protected val channel: JSelectableChannel

  final val provider: UIO[SelectorProvider] =
    IO.effectTotal(new SelectorProvider(channel.provider()))

  final val validOps: UIO[Set[Operation]] =
    IO.effectTotal(channel.validOps())
      .map(Operation.fromInt(_))

  final val isRegistered: UIO[Boolean] =
    IO.effectTotal(channel.isRegistered())

  final def keyFor(sel: Selector): UIO[Option[SelectionKey]] =
    IO.effectTotal(Option(channel.keyFor(sel.selector)).map(new SelectionKey(_)))

  final def register(sel: Selector, ops: Set[Operation], att: Option[AnyRef]): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, Operation.toInt(ops), att.orNull)))
      .refineToOrDie[IOException]

  final def register(sel: Selector, ops: Set[Operation]): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, Operation.toInt(ops))))
      .refineToOrDie[IOException]

  final def register(sel: Selector, op: Operation, att: Option[AnyRef]): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, op.intVal, att.orNull)))
      .refineToOrDie[IOException]

  final def register(sel: Selector, op: Operation): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, op.intVal)))
      .refineToOrDie[IOException]

  final def configureBlocking(block: Boolean): IO[IOException, Unit] =
    IO.effect(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  final val isBlocking: UIO[Boolean] =
    IO.effectTotal(channel.isBlocking())

  final val blockingLock: UIO[AnyRef] =
    IO.effectTotal(channel.blockingLock())
}

final class SocketChannel(override protected[channels] val channel: JSocketChannel)
    extends SelectableChannel
    with GatheringByteChannel
    with ScatteringByteChannel {

  def bindTo(address: SocketAddress): IO[IOException, Unit] = bind(Some(address))

  def bindAuto: IO[IOException, Unit] = bind(None)

  def bind(local: Option[SocketAddress]): IO[IOException, Unit] =
    IO.effect(channel.bind(local.map(_.jSocketAddress).orNull)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  val shutdownInput: IO[IOException, Unit] =
    IO.effect(channel.shutdownInput()).refineToOrDie[IOException].unit

  val shutdownOutput: IO[IOException, Unit] =
    IO.effect(channel.shutdownOutput()).refineToOrDie[IOException].unit

  val socket: UIO[JSocket] =
    IO.effectTotal(channel.socket())

  val isConnected: UIO[Boolean] =
    IO.effectTotal(channel.isConnected)

  val isConnectionPending: UIO[Boolean] =
    IO.effectTotal(channel.isConnectionPending)

  def connect(remote: SocketAddress): IO[IOException, Boolean] =
    IO.effect(channel.connect(remote.jSocketAddress)).refineToOrDie[IOException]

  val finishConnect: IO[IOException, Boolean] =
    IO.effect(channel.finishConnect()).refineToOrDie[IOException]

  val remoteAddress: IO[IOException, SocketAddress] =
    IO.effect(SocketAddress.fromJava(channel.getRemoteAddress())).refineToOrDie[IOException]

  val localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getLocalAddress()).map(SocketAddress.fromJava))
      .refineToOrDie[IOException]
}

object SocketChannel {

  def fromJava(javaSocketChannel: JSocketChannel): SocketChannel = new SocketChannel(javaSocketChannel)

  val open: Managed[IOException, SocketChannel] =
    IO.effect(new SocketChannel(JSocketChannel.open())).refineToOrDie[IOException].toNioManaged

  def open(remote: SocketAddress): Managed[IOException, SocketChannel] =
    IO.effect(new SocketChannel(JSocketChannel.open(remote.jSocketAddress))).refineToOrDie[IOException].toNioManaged
}

final class ServerSocketChannel(override protected val channel: JServerSocketChannel) extends SelectableChannel {
  def bindTo(local: SocketAddress, backlog: Int = 0): IO[IOException, Unit] = bind(Some(local), backlog)

  def bindAuto(backlog: Int = 0): IO[IOException, Unit] = bind(None, backlog)

  def bind(local: Option[SocketAddress], backlog: Int = 0): IO[IOException, Unit] =
    IO.effect(channel.bind(local.map(_.jSocketAddress).orNull, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  val socket: UIO[JServerSocket] =
    IO.effectTotal(channel.socket())

  /**
   * Accepts a socket connection.
   *
   * Note that the accept operation is not performed until the returned managed resource is
   * actually used. `Managed.preallocate` can be used to preform the accept immediately.
   *
   * @return None if this socket is in non-blocking mode and no connection is currently available to be accepted.
   */
  def accept: Managed[IOException, Option[SocketChannel]] =
    IO.effect(Option(channel.accept()).map(new SocketChannel(_)))
      .refineToOrDie[IOException]
      .toManaged(IO.whenCase(_) { case Some(channel) =>
        channel.close.ignore
      })

  val localAddress: IO[IOException, SocketAddress] =
    IO.effect(SocketAddress.fromJava(channel.getLocalAddress())).refineToOrDie[IOException]
}

object ServerSocketChannel {

  val open: Managed[IOException, ServerSocketChannel] =
    IO.effect(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException].toNioManaged

  def fromJava(javaChannel: JServerSocketChannel): ServerSocketChannel = new ServerSocketChannel(javaChannel)
}
