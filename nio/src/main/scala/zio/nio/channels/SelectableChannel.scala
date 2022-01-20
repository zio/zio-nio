package zio.nio
package channels

import zio.nio.channels.SelectionKey.Operation
import zio.nio.channels.spi.SelectorProvider
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Fiber, IO, Managed, UIO, ZIO, ZManaged, ZTraceElement}

import java.io.IOException
import java.net.{ServerSocket => JServerSocket, Socket => JSocket, SocketOption}
import java.nio.channels.{
  ClosedChannelException,
  SelectableChannel => JSelectableChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}

/**
 * A channel that can be multiplexed via a [[zio.nio.channels.Selector]].
 */
trait SelectableChannel extends BlockingChannel {

  /**
   * The non-blocking operations supported by this channel.
   */
  type NonBlockingOps

  protected val channel: JSelectableChannel

  final def provider(implicit trace: ZTraceElement): UIO[SelectorProvider] =
    IO.succeed(new SelectorProvider(channel.provider()))

  final def validOps(implicit trace: ZTraceElement): UIO[Set[Operation]] =
    IO.succeed(channel.validOps())
      .map(Operation.fromInt(_))

  final def isRegistered(implicit trace: ZTraceElement): UIO[Boolean] =
    IO.succeed(channel.isRegistered())

  final def keyFor(sel: Selector)(implicit trace: ZTraceElement): UIO[Option[SelectionKey]] =
    IO.succeed(Option(channel.keyFor(sel.selector)).map(new SelectionKey(_)))

  /**
   * Registers this channel with the given selector, returning a selection key.
   *
   * @param selector
   *   The selector to register with.
   * @param ops
   *   The key's interest set will be created with these operations.
   * @param attachment
   *   The object to attach to the key, if any.
   * @return
   *   The new `SelectionKey`.
   */
  final def register(
    selector: Selector,
    ops: Set[Operation] = Set.empty,
    attachment: Option[AnyRef] = None
  )(implicit trace: ZTraceElement): IO[ClosedChannelException, SelectionKey] =
    IO.attempt(new SelectionKey(channel.register(selector.selector, Operation.toInt(ops), attachment.orNull)))
      .refineToOrDie[ClosedChannelException]

  final def configureBlocking(block: Boolean)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  final def isBlocking(implicit trace: ZTraceElement): UIO[Boolean] =
    IO.succeed(channel.isBlocking())

  final def blockingLock(implicit trace: ZTraceElement): UIO[AnyRef] =
    IO.succeed(channel.blockingLock())

  protected def makeBlockingOps: BlockingOps

  final override def useBlocking[R, E >: IOException, A](f: BlockingOps => ZIO[R, E, A])(implicit
    trace: ZTraceElement
  ): ZIO[R with Any, E, A] =
    configureBlocking(true) *> nioBlocking(f(makeBlockingOps))

  protected def makeNonBlockingOps: NonBlockingOps

  /**
   * Puts this channel into non-blocking mode and performs a set of non-blocking operations.
   *
   * @param f
   *   Uses the `NonBlockingOps` appropriate for this channel type to produce non-blocking effects.
   */
  final def useNonBlocking[R, E >: IOException, A](f: NonBlockingOps => ZIO[R, E, A])(implicit
    trace: ZTraceElement
  ): ZIO[R, E, A] =
    configureBlocking(false) *> f(makeNonBlockingOps)

  /**
   * Puts this channel into non-blocking mode and performs a set of non-blocking operations as a managed resource.
   *
   * @param f
   *   Uses the `NonBlockingOps` appropriate for this channel type to produce non-blocking effects.
   */
  final def useNonBlockingManaged[R, E >: IOException, A](f: NonBlockingOps => ZManaged[R, E, A])(implicit
    trace: ZTraceElement
  ): ZManaged[R, E, A] =
    configureBlocking(false).toManaged *> f(makeNonBlockingOps)

}

final class SocketChannel(override protected[channels] val channel: JSocketChannel) extends SelectableChannel {

  self =>

  override type BlockingOps = BlockingSocketOps

  override type NonBlockingOps = NonBlockingSocketOps

  sealed abstract class Ops extends GatheringByteOps with ScatteringByteOps {
    override protected[channels] def channel = self.channel
  }

  final class BlockingSocketOps private[SocketChannel] () extends Ops {

    def connect(remote: SocketAddress)(implicit trace: ZTraceElement): IO[IOException, Unit] =
      IO.attempt(self.channel.connect(remote.jSocketAddress)).refineToOrDie[IOException].unit

  }

  override protected def makeBlockingOps = new BlockingSocketOps

  final class NonBlockingSocketOps private[SocketChannel] () extends Ops {

    def isConnectionPending(implicit trace: ZTraceElement): UIO[Boolean] = IO.succeed(self.channel.isConnectionPending)

    def connect(remote: SocketAddress)(implicit trace: ZTraceElement): IO[IOException, Boolean] =
      IO.attempt(self.channel.connect(remote.jSocketAddress)).refineToOrDie[IOException]

    def finishConnect(implicit trace: ZTraceElement): IO[IOException, Boolean] =
      IO.attempt(self.channel.finishConnect()).refineToOrDie[IOException]

  }

  override protected def makeNonBlockingOps = new NonBlockingSocketOps

  def bindTo(address: SocketAddress)(implicit trace: ZTraceElement): IO[IOException, Unit] = bind(Some(address))

  def bindAuto(implicit trace: ZTraceElement): IO[IOException, Unit] = bind(None)

  def bind(local: Option[SocketAddress])(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(channel.bind(local.map(_.jSocketAddress).orNull)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(channel.setOption(name, value)).refineToOrDie[IOException].unit

  def shutdownInput(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(channel.shutdownInput()).refineToOrDie[IOException].unit

  def shutdownOutput(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(channel.shutdownOutput()).refineToOrDie[IOException].unit

  def socket(implicit trace: ZTraceElement): UIO[JSocket] = IO.succeed(channel.socket())

  def isConnected(implicit trace: ZTraceElement): UIO[Boolean] = IO.succeed(channel.isConnected)

  def remoteAddress(implicit trace: ZTraceElement): IO[IOException, SocketAddress] =
    IO.attempt(SocketAddress.fromJava(channel.getRemoteAddress())).refineToOrDie[IOException]

  def localAddress(implicit trace: ZTraceElement): IO[IOException, Option[SocketAddress]] =
    IO.attempt(Option(channel.getLocalAddress()).map(SocketAddress.fromJava))
      .refineToOrDie[IOException]

}

object SocketChannel {

  def fromJava(javaSocketChannel: JSocketChannel): SocketChannel = new SocketChannel(javaSocketChannel)

  def open(implicit trace: ZTraceElement): Managed[IOException, SocketChannel] =
    IO.attempt(new SocketChannel(JSocketChannel.open())).refineToOrDie[IOException].toNioManaged

  def open(remote: SocketAddress)(implicit trace: ZTraceElement): Managed[IOException, SocketChannel] =
    IO.attempt(new SocketChannel(JSocketChannel.open(remote.jSocketAddress))).refineToOrDie[IOException].toNioManaged

}

final class ServerSocketChannel(override protected val channel: JServerSocketChannel) extends SelectableChannel {

  override type BlockingOps = BlockingServerSocketOps

  override type NonBlockingOps = NonBlockingServerSocketOps

  final class BlockingServerSocketOps private[ServerSocketChannel] () {

    /**
     * Accepts a socket connection.
     *
     * Note that the accept operation is not performed until the returned managed resource is actually used.
     * `Managed.preallocate` can be used to preform the accept immediately.
     *
     * @return
     *   The channel for the accepted socket connection.
     */
    def accept(implicit trace: ZTraceElement): Managed[IOException, SocketChannel] =
      IO.attempt(new SocketChannel(channel.accept())).refineToOrDie[IOException].toNioManaged

    /**
     * Accepts a connection and uses it to perform an effect on a forked fiber.
     *
     * @param use
     *   Uses the accepted socket channel to produce an effect value, which will be run on a forked fiber.
     * @return
     *   The fiber running the effect.
     */
    def acceptAndFork[R, A](
      use: SocketChannel => ZIO[R, IOException, A]
    )(implicit trace: ZTraceElement): ZIO[R, IOException, Fiber[IOException, A]] = accept.useForked(use)

  }

  override protected def makeBlockingOps: BlockingServerSocketOps = new BlockingServerSocketOps

  final class NonBlockingServerSocketOps private[ServerSocketChannel] () {

    /**
     * Accepts a socket connection.
     *
     * Note that the accept operation is not performed until the returned managed resource is actually used.
     * `Managed.preallocate` can be used to preform the accept immediately.
     *
     * @return
     *   None if no connection is currently available to be accepted.
     */
    def accept(implicit trace: ZTraceElement): Managed[IOException, Option[SocketChannel]] =
      IO.attempt(Option(channel.accept()).map(new SocketChannel(_)))
        .refineToOrDie[IOException]
        .toManagedWith(IO.whenCase(_) { case Some(channel) =>
          channel.close.ignore
        })

  }

  override protected def makeNonBlockingOps: NonBlockingServerSocketOps = new NonBlockingServerSocketOps

  def bindTo(local: SocketAddress, backlog: Int = 0)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    bind(Some(local), backlog)

  def bindAuto(backlog: Int = 0)(implicit trace: ZTraceElement): IO[IOException, Unit] = bind(None, backlog)

  def bind(local: Option[SocketAddress], backlog: Int = 0)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(channel.bind(local.map(_.jSocketAddress).orNull, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(channel.setOption(name, value)).refineToOrDie[IOException].unit

  def socket(implicit trace: ZTraceElement): UIO[JServerSocket] =
    IO.succeed(channel.socket())

  def localAddress(implicit trace: ZTraceElement): IO[IOException, SocketAddress] =
    IO.attempt(SocketAddress.fromJava(channel.getLocalAddress())).refineToOrDie[IOException]

}

object ServerSocketChannel {

  def open(implicit trace: ZTraceElement): Managed[IOException, ServerSocketChannel] =
    IO.attempt(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException].toNioManaged

  def fromJava(javaChannel: JServerSocketChannel): ServerSocketChannel = new ServerSocketChannel(javaChannel)
}
