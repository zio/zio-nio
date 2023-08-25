package zio.nio
package channels
import zio._

import java.io.IOException
import java.net.SocketOption
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel
}

/**
 * A byte channel that reads and writes asynchronously.
 *
 * The read and write operations will never block the calling thread.
 */
abstract class AsynchronousByteChannel private[channels] (protected val channel: JAsynchronousByteChannel)
    extends Channel {}

object AsynchronousByteChannel {}

final class AsynchronousServerSocketChannel(protected val channel: JAsynchronousServerSocketChannel) extends Channel {

  def bindTo(local: SocketAddress, backlog: Int = 0)(implicit trace: Trace): IO[IOException, Unit] =
    bind(Some(local), backlog)

  def bindAuto(backlog: Int = 0)(implicit trace: Trace): IO[IOException, Unit] = bind(None, backlog)

  /**
   * Binds the channel's socket to a local address and configures the socket to listen for connections, up to backlog
   * pending connection.
   */
  def bind(address: Option[SocketAddress], backlog: Int = 0)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(address.map(_.jSocketAddress).orNull, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[IOException].unit

  /**
   * The `SocketAddress` that the socket is bound to, or the `SocketAddress` representing the loopback address if denied
   * by the security manager, or `Maybe.empty` if the channel's socket is not bound.
   */
  def localAddress(implicit trace: Trace): IO[IOException, Option[SocketAddress]] =
    ZIO
      .attempt(
        Option(channel.getLocalAddress).map(SocketAddress.fromJava)
      )
      .refineToOrDie[IOException]

}

object AsynchronousServerSocketChannel {

  def open(implicit trace: Trace): ZIO[Scope, IOException, AsynchronousServerSocketChannel] =
    ZIO
      .attempt(new AsynchronousServerSocketChannel(JAsynchronousServerSocketChannel.open()))
      .refineToOrDie[IOException]
      .toNioScoped

  def open(
    channelGroup: AsynchronousChannelGroup
  )(implicit trace: Trace): ZIO[Scope, IOException, AsynchronousServerSocketChannel] =
    ZIO
      .attempt(new AsynchronousServerSocketChannel(JAsynchronousServerSocketChannel.open(channelGroup.channelGroup)))
      .refineToOrDie[IOException]
      .toNioScoped

  def fromJava(channel: JAsynchronousServerSocketChannel): AsynchronousServerSocketChannel =
    new AsynchronousServerSocketChannel(channel)

}

final class AsynchronousSocketChannel(override protected val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  def bindTo(address: SocketAddress)(implicit trace: Trace): IO[IOException, Unit] = bind(Some(address))

  def bindAuto(implicit trace: Trace): IO[IOException, Unit] = bind(None)

  def bind(address: Option[SocketAddress])(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.bind(address.map(_.jSocketAddress).orNull)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.setOption(name, value)).refineToOrDie[IOException].unit

  def shutdownInput(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.shutdownInput()).refineToOrDie[IOException].unit

  def shutdownOutput(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.shutdownOutput()).refineToOrDie[IOException].unit

  def remoteAddress(implicit trace: Trace): IO[IOException, Option[SocketAddress]] =
    ZIO
      .attempt(
        Option(channel.getRemoteAddress)
          .map(SocketAddress.fromJava)
      )
      .refineToOrDie[IOException]

  def localAddress(implicit trace: Trace): IO[IOException, Option[SocketAddress]] =
    ZIO
      .attempt(
        Option(channel.getLocalAddress)
          .map(SocketAddress.fromJava)
      )
      .refineToOrDie[IOException]

}

object AsynchronousSocketChannel {

  def open(implicit trace: Trace): ZIO[Scope, IOException, AsynchronousSocketChannel] =
    ZIO
      .attempt(new AsynchronousSocketChannel(JAsynchronousSocketChannel.open()))
      .refineToOrDie[IOException]
      .toNioScoped

  def open(
    channelGroup: AsynchronousChannelGroup
  )(implicit trace: Trace): ZIO[Scope, IOException, AsynchronousSocketChannel] =
    ZIO
      .attempt(new AsynchronousSocketChannel(JAsynchronousSocketChannel.open(channelGroup.channelGroup)))
      .refineToOrDie[IOException]
      .toNioScoped

  def fromJava(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)

}
