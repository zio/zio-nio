package zio.nio
package channels

import java.io.IOException
import java.net.{ SocketOption, ServerSocket => JServerSocket, Socket => JSocket }
import java.nio.channels.{
  ClosedChannelException,
  SelectableChannel => JSelectableChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}

import zio.blocking.Blocking
import zio.{ Fiber, IO, Managed, UIO, ZIO, ZManaged }
import zio.nio.channels.SelectionKey.Operation
import zio.nio.channels.spi.SelectorProvider

/**
 * A channel that can be multiplexed via a [[zio.nio.channels.Selector]].
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

  /**
   * Registers this channel with the given selector, returning a selection key.
   *
   * @param selector The selector to register with.
   * @param ops The key's interest set will be created with these operations.
   * @param attachment The object to attach to the key, if any.
   * @return The new `SelectionKey`.
   */
  final def register(
    selector: Selector,
    ops: Set[Operation] = Set.empty,
    attachment: Option[AnyRef] = None
  ): IO[ClosedChannelException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(selector.selector, Operation.toInt(ops), attachment.orNull)))
      .refineToOrDie[ClosedChannelException]

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

  def bindTo(address: SocketAddress): IO[IOException, Unit] = bind(Some(address))

  def bindAuto: IO[IOException, Unit] = bind(None)

  def bind(local: Option[SocketAddress]): IO[IOException, Unit] =
    IO.effect(channel.bind(local.map(_.jSocketAddress).orNull)).refineToOrDie[IOException].unit

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

    /**
     * Accepts a socket connection.
     *
     * Note that the accept operation is not performed until the returned managed resource is
     * actually used. `Managed.preallocate` can be used to preform the accept immediately.
     *
     * @return The channel for the accepted socket connection.
     */
    def accept: Managed[IOException, SocketChannel] =
      IO.effect(new SocketChannel(channel.accept())).refineToOrDie[IOException].toNioManaged

    /**
     * Accepts a connection and uses it to perform an effect on a forked fiber.
     *
     * @param use Uses the accepted socket channel to produce an effect value, which will be run on a forked fiber.
     * @return The fiber running the effect.
     */
    def acceptAndFork[R, A](
      use: SocketChannel => ZIO[R, IOException, A]
    ): ZIO[R, IOException, Fiber[IOException, A]] = accept.useForked(use)

  }

  override protected def makeBlockingOps: BlockingServerSocketOps = new BlockingServerSocketOps

  final class NonBlockingServerSocketOps private[ServerSocketChannel] () {

    /**
     * Accepts a socket connection.
     *
     * Note that the accept operation is not performed until the returned managed resource is
     * actually used. `Managed.preallocate` can be used to preform the accept immediately.
     *
     * @return None if no connection is currently available to be accepted.
     */
    def accept: Managed[IOException, Option[SocketChannel]] =
      IO.effect(Option(channel.accept()).map(new SocketChannel(_)))
        .refineToOrDie[IOException]
        .toManaged(IO.whenCase(_) {
          case Some(channel) => channel.close.ignore
        })

  }

  override protected def makeNonBlockingOps: NonBlockingServerSocketOps = new NonBlockingServerSocketOps

  def bindTo(local: SocketAddress, backlog: Int = 0): IO[IOException, Unit] = bind(Some(local), backlog)

  def bindAuto(backlog: Int = 0): IO[IOException, Unit] = bind(None, backlog)

  def bind(local: Option[SocketAddress], backlog: Int = 0): IO[IOException, Unit] =
    IO.effect(channel.bind(local.map(_.jSocketAddress).orNull, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  val socket: UIO[JServerSocket] =
    IO.effectTotal(channel.socket())

  val localAddress: IO[IOException, SocketAddress] =
    IO.effect(SocketAddress.fromJava(channel.getLocalAddress())).refineToOrDie[IOException]
}

object ServerSocketChannel {

  val open: Managed[IOException, ServerSocketChannel] =
    IO.effect(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException].toNioManaged

  def fromJava(javaChannel: JServerSocketChannel): ServerSocketChannel =
    new ServerSocketChannel(javaChannel)
}
