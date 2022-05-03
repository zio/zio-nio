package zio.nio
package channels

import zio.nio.channels.SelectionKey.Operation
import zio.nio.channels.spi.SelectorProvider
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Exit, Fiber, IO, Scope, Trace, UIO, ZIO}

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

  final def provider(implicit trace: Trace): UIO[SelectorProvider] =
    ZIO.succeed(new SelectorProvider(channel.provider()))

  final def validOps(implicit trace: Trace): UIO[Set[Operation]] =
    ZIO
      .succeed(channel.validOps())
      .map(Operation.fromInt(_))

  final def isRegistered(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(channel.isRegistered())

  final def keyFor(sel: Selector)(implicit trace: Trace): UIO[Option[SelectionKey]] =
    ZIO.succeed(Option(channel.keyFor(sel.selector)).map(new SelectionKey(_)))

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
  )(implicit trace: Trace): IO[ClosedChannelException, SelectionKey] =
    ZIO
      .attempt(new SelectionKey(channel.register(selector.selector, Operation.toInt(ops), attachment.orNull)))
      .refineToOrDie[ClosedChannelException]

  final def configureBlocking(block: Boolean)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  final def isBlocking(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(channel.isBlocking())

  final def blockingLock(implicit trace: Trace): UIO[AnyRef] =
    ZIO.succeed(channel.blockingLock())

  protected def makeBlockingOps: BlockingOps

  final override def flatMapBlocking[R, E >: IOException, A](f: BlockingOps => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R with Any, E, A] =
    configureBlocking(true) *> nioBlocking(f(makeBlockingOps))

  protected def makeNonBlockingOps: NonBlockingOps

  /**
   * Puts this channel into non-blocking mode and performs a set of non-blocking operations.
   *
   * @param f
   *   Uses the `NonBlockingOps` appropriate for this channel type to produce non-blocking effects.
   */
  final def flatMapNonBlocking[R, E >: IOException, A](f: NonBlockingOps => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R, E, A] =
    configureBlocking(false) *> f(makeNonBlockingOps)

}

final class SocketChannel(override protected[channels] val channel: JSocketChannel) extends SelectableChannel {

  self =>

  override type BlockingOps = BlockingSocketOps

  override type NonBlockingOps = NonBlockingSocketOps

  sealed abstract class Ops extends GatheringByteOps with ScatteringByteOps {
    override protected[channels] def channel = self.channel
  }

  final class BlockingSocketOps private[SocketChannel] () extends Ops {

    def connect(remote: SocketAddress)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.attempt(self.channel.connect(remote.jSocketAddress)).refineToOrDie[IOException].unit

  }

  override protected def makeBlockingOps = new BlockingSocketOps

  final class NonBlockingSocketOps private[SocketChannel] () extends Ops {

    def isConnectionPending(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(self.channel.isConnectionPending)

    def connect(remote: SocketAddress)(implicit trace: Trace): IO[IOException, Boolean] =
      ZIO.attempt(self.channel.connect(remote.jSocketAddress)).refineToOrDie[IOException]

    def finishConnect(implicit trace: Trace): IO[IOException, Boolean] =
      ZIO.attempt(self.channel.finishConnect()).refineToOrDie[IOException]

  }

  override protected def makeNonBlockingOps = new NonBlockingSocketOps

  def bindTo(address: SocketAddress)(implicit trace: Trace): IO[IOException, Unit] = bind(Some(address))

  def bindAuto(implicit trace: Trace): IO[IOException, Unit] = bind(None)

  def bind(local: Option[SocketAddress])(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(local.map(_.jSocketAddress).orNull)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[IOException].unit

  def shutdownInput(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.shutdownInput()).refineToOrDie[IOException].unit

  def shutdownOutput(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.shutdownOutput()).refineToOrDie[IOException].unit

  def socket(implicit trace: Trace): UIO[JSocket] = ZIO.succeed(channel.socket())

  def isConnected(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(channel.isConnected)

  def remoteAddress(implicit trace: Trace): IO[IOException, SocketAddress] =
    ZIO.attempt(SocketAddress.fromJava(channel.getRemoteAddress())).refineToOrDie[IOException]

  def localAddress(implicit trace: Trace): IO[IOException, Option[SocketAddress]] =
    ZIO
      .attempt(Option(channel.getLocalAddress()).map(SocketAddress.fromJava))
      .refineToOrDie[IOException]

}

object SocketChannel {

  def fromJava(javaSocketChannel: JSocketChannel): SocketChannel = new SocketChannel(javaSocketChannel)

  def open(implicit trace: Trace): ZIO[Scope, IOException, SocketChannel] =
    ZIO.attempt(new SocketChannel(JSocketChannel.open())).refineToOrDie[IOException].toNioScoped

  def open(remote: SocketAddress)(implicit trace: Trace): ZIO[Scope, IOException, SocketChannel] =
    ZIO.attempt(new SocketChannel(JSocketChannel.open(remote.jSocketAddress))).refineToOrDie[IOException].toNioScoped

}

final class ServerSocketChannel(override protected val channel: JServerSocketChannel) extends SelectableChannel {

  override type BlockingOps = BlockingServerSocketOps

  override type NonBlockingOps = NonBlockingServerSocketOps

  final class BlockingServerSocketOps private[ServerSocketChannel] () {

    /**
     * Accepts a socket connection.
     *
     * @return
     *   The channel for the accepted socket connection.
     */
    def accept(implicit trace: Trace): ZIO[Scope, IOException, SocketChannel] =
      ZIO.attempt(new SocketChannel(channel.accept())).refineToOrDie[IOException].toNioScoped

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
    )(implicit trace: Trace): ZIO[R, IOException, Fiber[IOException, A]] =
      ZIO.uninterruptibleMask { restore =>
        Scope.make.flatMap { scope =>
          scope
            .extend(restore(accept))
            .foldCauseZIO(
              cause => scope.close(Exit.failCause(cause)) *> ZIO.failCause(cause),
              socketChannel => scope.use[R](restore(use(socketChannel))).fork
            )
        }
      }
  }

  override protected def makeBlockingOps: BlockingServerSocketOps = new BlockingServerSocketOps

  final class NonBlockingServerSocketOps private[ServerSocketChannel] () {

    /**
     * Accepts a socket connection.
     *
     * @return
     *   None if no connection is currently available to be accepted.
     */
    def accept(implicit trace: Trace): ZIO[Scope, IOException, Option[SocketChannel]] =
      ZIO.acquireRelease {
        ZIO
          .attempt(Option(channel.accept()).map(new SocketChannel(_)))
          .refineToOrDie[IOException]
      } {
        ZIO.whenCase(_) { case Some(channel) =>
          channel.close.ignore
        }
      }
  }

  override protected def makeNonBlockingOps: NonBlockingServerSocketOps = new NonBlockingServerSocketOps

  def bindTo(local: SocketAddress, backlog: Int = 0)(implicit trace: Trace): IO[IOException, Unit] =
    bind(Some(local), backlog)

  def bindAuto(backlog: Int = 0)(implicit trace: Trace): IO[IOException, Unit] = bind(None, backlog)

  def bind(local: Option[SocketAddress], backlog: Int = 0)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(local.map(_.jSocketAddress).orNull, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[IOException].unit

  def socket(implicit trace: Trace): UIO[JServerSocket] =
    ZIO.succeed(channel.socket())

  def localAddress(implicit trace: Trace): IO[IOException, SocketAddress] =
    ZIO.attempt(SocketAddress.fromJava(channel.getLocalAddress())).refineToOrDie[IOException]

}

object ServerSocketChannel {

  def open(implicit trace: Trace): ZIO[Scope, IOException, ServerSocketChannel] =
    ZIO.attempt(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException].toNioScoped

  def fromJava(javaChannel: JServerSocketChannel): ServerSocketChannel = new ServerSocketChannel(javaChannel)
}
