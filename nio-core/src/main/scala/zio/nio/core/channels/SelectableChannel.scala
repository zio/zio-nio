package zio.nio.core
package channels

import java.io.IOException
import java.net.{ SocketOption, ServerSocket => JServerSocket, Socket => JSocket }
import java.nio.channels.{
  SelectableChannel => JSelectableChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}

import zio.blocking.Blocking
import zio.{ IO, Managed, UIO, ZIO, ZManaged }
import zio.nio.core.channels.SelectionKey.Operation
import zio.nio.core.channels.spi.SelectorProvider

/**
 * A channel that can be multiplexed via a [[zio.nio.core.channels.Selector]].
 */
trait SelectableChannel extends BlockingChannel {

  /**
   * The non-blocking operations supported by this channel.
   */
  type NonBlockingOps

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

  protected def makeBlockingOps: BlockingOps

  final override def useBlocking[R, E >: IOException, A](f: BlockingOps => ZIO[R, E, A]): ZIO[R with Blocking, E, A] =
    configureBlocking(true) *> nioBlocking(f(makeBlockingOps))

  protected def makeNonBlockingOps: NonBlockingOps

  /**
   * Puts this channel into non-blocking mode and performs a set of non-blocking operations.
   *
   * @param f Uses the `NonBlockingOps` appropriate for this channel type to produce non-blocking effects.
   */
  final def useNonBlocking[R, E >: IOException, A](f: NonBlockingOps => ZIO[R, E, A]): ZIO[R, E, A] =
    configureBlocking(false) *> f(makeNonBlockingOps)

  /**
   * Puts this channel into non-blocking mode and performs a set of non-blocking operations as a managed resource.
   *
   * @param f Uses the `NonBlockingOps` appropriate for this channel type to produce non-blocking effects.
   */
  final def useNonBlockingManaged[R, E >: IOException, A](f: NonBlockingOps => ZManaged[R, E, A]): ZManaged[R, E, A] =
    configureBlocking(false).toManaged_ *> f(makeNonBlockingOps)

}

final class SocketChannel(override protected[channels] val channel: JSocketChannel) extends SelectableChannel {

  self =>

  override type BlockingOps = BlockingSocketOps

  override type NonBlockingOps = NonBlockingSocketOps

  sealed abstract class Ops extends GatheringByteOps with ScatteringByteOps {
    override protected[channels] def channel = self.channel
  }

  final class BlockingSocketOps private[SocketChannel] () extends Ops {

    def connect(remote: SocketAddress): IO[IOException, Unit] =
      IO.effect(channel.connect(remote.jSocketAddress)).refineToOrDie[IOException].unit

  }

  override protected def makeBlockingOps = new BlockingSocketOps

  final class NonBlockingSocketOps private[SocketChannel] () extends Ops {

    def isConnectionPending: UIO[Boolean] =
      IO.effectTotal(channel.isConnectionPending)

    def connect(remote: SocketAddress): IO[IOException, Boolean] =
      IO.effect(channel.connect(remote.jSocketAddress)).refineToOrDie[IOException]

    def finishConnect: IO[IOException, Boolean] =
      IO.effect(channel.finishConnect()).refineToOrDie[IOException]

  }

  override protected def makeNonBlockingOps = new NonBlockingSocketOps

  def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  def shutdownInput: IO[IOException, Unit] =
    IO.effect(channel.shutdownInput()).refineToOrDie[IOException].unit

  def shutdownOutput: IO[IOException, Unit] =
    IO.effect(channel.shutdownOutput()).refineToOrDie[IOException].unit

  def socket: UIO[JSocket] =
    IO.effectTotal(channel.socket())

  def isConnected: UIO[Boolean] =
    IO.effectTotal(channel.isConnected)

  def remoteAddress: IO[IOException, SocketAddress] =
    IO.effect(SocketAddress.fromJava(channel.getRemoteAddress())).refineToOrDie[IOException]

  def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getLocalAddress()).map(SocketAddress.fromJava))
      .refineToOrDie[IOException]
}

object SocketChannel {

  def fromJava(javaSocketChannel: JSocketChannel): SocketChannel =
    new SocketChannel(javaSocketChannel)

  val open: Managed[IOException, SocketChannel] =
    IO.effect(new SocketChannel(JSocketChannel.open())).refineToOrDie[IOException].toNioManaged

  def open(remote: SocketAddress): Managed[IOException, SocketChannel] =
    IO.effect(new SocketChannel(JSocketChannel.open(remote.jSocketAddress))).refineToOrDie[IOException].toNioManaged
}

final class ServerSocketChannel(override protected val channel: JServerSocketChannel) extends SelectableChannel {

  override type BlockingOps = BlockingServerSocketOps

  override type NonBlockingOps = NonBlockingServerSocketOps

  final class BlockingServerSocketOps private[ServerSocketChannel] () {

    def accept: Managed[IOException, SocketChannel] =
      IO.effect(new SocketChannel(channel.accept())).refineToOrDie[IOException].toNioManaged

  }

  override protected def makeBlockingOps: BlockingServerSocketOps = new BlockingServerSocketOps

  final class NonBlockingServerSocketOps private[ServerSocketChannel] () {

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
        .toManaged(IO.whenCase(_) {
          case Some(channel) => channel.close.ignore
        })

  }

  override protected def makeNonBlockingOps: NonBlockingServerSocketOps = new NonBlockingServerSocketOps

  def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress)).refineToOrDie[IOException].unit

  def bind(local: SocketAddress, backlog: Int): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  val socket: UIO[JServerSocket] =
    IO.effectTotal(channel.socket())

  val localAddress: IO[IOException, SocketAddress] =
    IO.effect(new SocketAddress(channel.getLocalAddress())).refineToOrDie[IOException]
}

object ServerSocketChannel {

  val open: Managed[IOException, ServerSocketChannel] =
    IO.effect(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException].toNioManaged

  def fromJava(javaChannel: JServerSocketChannel): ServerSocketChannel =
    new ServerSocketChannel(javaChannel)
}
