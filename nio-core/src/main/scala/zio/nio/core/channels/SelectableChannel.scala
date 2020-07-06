package zio.nio.core.channels

import java.io.IOException
import java.net.{ SocketOption, ServerSocket => JServerSocket, Socket => JSocket }
import java.nio.channels.{
  SelectableChannel => JSelectableChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}

import zio.{ IO, Managed, UIO, ZIO, blocking }
import zio.nio.core.channels.SelectionKey.Operation
import zio.nio.core.channels.spi.SelectorProvider
import zio.nio.core.SocketAddress

/**
 * A channel that can be in either blocking or non-blocking mode.
 */
trait ModalChannel extends Channel {

  protected val channel: JSelectableChannel

  final def provider: SelectorProvider = new SelectorProvider(channel.provider())

  final def isBlocking: UIO[Boolean] =
    IO.effectTotal(channel.isBlocking())

  final def blockingLock: UIO[AnyRef] =
    IO.effectTotal(channel.blockingLock())
}

/**
 * A channel that can be multiplexed via a [[zio.nio.core.channels.Selector]].
 */
trait SelectableChannel extends ModalChannel with WithEnv.NonBlocking {

  final def validOps: Set[Operation] =
    Operation.fromInt(channel.validOps())

  final def isRegistered: UIO[Boolean] =
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

}

sealed abstract class SocketChannel[R](override protected[channels] val channel: JSocketChannel)
    extends ModalChannel
    with GatheringByteChannel[R]
    with ScatteringByteChannel[R] {

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

  def remoteAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getRemoteAddress()).map(SocketAddress.fromJava))
      .refineToOrDie[IOException]

  def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getLocalAddress()).map(SocketAddress.fromJava))
      .refineToOrDie[IOException]
}

object SocketChannel {

  final class Blocking private[SocketChannel] (c: JSocketChannel)
      extends SocketChannel[blocking.Blocking](c)
      with WithEnv.Blocking {

    def nonBlockingMode: IO[IOException, NonBlocking] =
      IO.effect(c.configureBlocking(false))
        .refineToOrDie[IOException]
        .as(new NonBlocking(c))

    def connect(remote: SocketAddress): ZIO[blocking.Blocking, IOException, Unit] =
      IO.effect(channel.connect(remote.jSocketAddress)).refineToOrDie[IOException].unit

  }

  object Blocking {

    def fromJava(javaSocketChannel: JSocketChannel): Blocking =
      new Blocking(javaSocketChannel)

    def open: IO[IOException, Blocking] =
      IO.effect(fromJava(JSocketChannel.open())).refineToOrDie[IOException]

    def open(remote: SocketAddress): ZIO[blocking.Blocking, IOException, Blocking] =
      blocking
        .effectBlockingInterrupt(fromJava(JSocketChannel.open(remote.jSocketAddress)))
        .refineToOrDie[IOException]

  }

  final class NonBlocking private[SocketChannel] (c: JSocketChannel)
      extends SocketChannel[Any](c)
      with SelectableChannel {

    def blockingMode: IO[IOException, Blocking] =
      IO.effect(c.configureBlocking(true))
        .refineToOrDie[IOException]
        .as(new Blocking(c))

    def connect(remote: SocketAddress): IO[IOException, Boolean] =
      IO.effect(channel.connect(remote.jSocketAddress)).refineToOrDie[IOException]

    def isConnectionPending: UIO[Boolean] =
      IO.effectTotal(channel.isConnectionPending)

    def finishConnect: IO[IOException, Boolean] =
      IO.effect(channel.finishConnect()).refineToOrDie[IOException]

  }

  object NonBlocking {

    def fromJava(javaSocketChannel: JSocketChannel): NonBlocking =
      new NonBlocking(javaSocketChannel)

    def open: IO[IOException, NonBlocking] =
      IO.effect {
        val javaChannel = JSocketChannel.open()
        javaChannel.configureBlocking(false)
        fromJava(javaChannel)
      }.refineToOrDie[IOException]
  }
}

sealed abstract class ServerSocketChannel[R](override protected val channel: JServerSocketChannel)
    extends ModalChannel {

  def bind(local: SocketAddress, backlog: Int = 0): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  def socket: UIO[JServerSocket] =
    IO.effectTotal(channel.socket())

  def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getLocalAddress()).map(new SocketAddress(_))).refineToOrDie[IOException]

}

object ServerSocketChannel {

  final class Blocking private[ServerSocketChannel] (c: JServerSocketChannel)
      extends ServerSocketChannel[blocking.Blocking](c)
      with WithEnv.Blocking {

    def nonBlockingMode: IO[IOException, NonBlocking] =
      IO.effect(c.configureBlocking(false))
        .refineToOrDie[IOException]
        .as(new NonBlocking(c))

    def accept: ZIO[blocking.Blocking, IOException, SocketChannel.Blocking] =
      isBlocking.filterOrDie(identity)(new IllegalStateException("Blocking socket in non-blocking mode")) *>
        IO.effect(SocketChannel.Blocking.fromJava(c.accept())).refineToOrDie[IOException]

  }

  object Blocking {

    def fromJava(javaChannel: JServerSocketChannel): Blocking =
      new Blocking(javaChannel)

    def open: IO[IOException, Blocking] =
      IO.effect(fromJava(JServerSocketChannel.open())).refineToOrDie[IOException]

  }

  final class NonBlocking private[ServerSocketChannel] (c: JServerSocketChannel)
      extends ServerSocketChannel[Any](c)
      with SelectableChannel {

    def blockingMode: IO[IOException, Blocking] =
      IO.effect(c.configureBlocking(true))
        .refineToOrDie[IOException]
        .as(new Blocking(c))

    private def assertNonBlocking =
      isBlocking.filterOrDie(!_)(new IllegalStateException("Non-blocking socket in blocking mode")).unit

    def accept: IO[IOException, Option[SocketChannel.Blocking]] =
      assertNonBlocking *>
        IO.effect(Option(c.accept()).map(SocketChannel.Blocking.fromJava)).refineToOrDie[IOException]

    def acceptNonBlocking: IO[IOException, Option[SocketChannel.NonBlocking]] =
      assertNonBlocking *>
        IO.effect {
          Option(c.accept()).map { javaChannel =>
            javaChannel.configureBlocking(false)
            SocketChannel.NonBlocking.fromJava(javaChannel)
          }
        }.refineToOrDie[IOException]

    def acceptBracket[R, A](f: SocketChannel.NonBlocking => ZIO[R, IOException, A]): ZIO[R, IOException, Option[A]] =
      acceptNonBlocking.some.flatMap(f.andThen(_.asSomeError)).optional

    def acceptBracket_[R](f: SocketChannel.NonBlocking => ZIO[R, IOException, Unit]): ZIO[R, IOException, Unit] =
      acceptNonBlocking.some.flatMap(f.andThen(_.asSomeError)).optional.someOrElse(())

    def acceptManaged: Managed[IOException, Option[SocketChannel.NonBlocking]] =
      Managed.make(acceptNonBlocking.some)(_.close.ignore).optional

  }

  object NonBlocking {

    def fromJava(javaChannel: JServerSocketChannel): NonBlocking = new NonBlocking(javaChannel)

    def open: IO[IOException, NonBlocking] =
      IO.effect {
        val javaChannel = JServerSocketChannel.open()
        javaChannel.configureBlocking(false)
        fromJava(javaChannel)
      }.refineToOrDie[IOException]

  }

}
