package zio.nio.channels

import zio.nio.core.{ ByteBuffer, SocketAddress }
import zio.{ IO, Managed, UIO }

import java.io.IOException
import java.net.{ DatagramSocket => JDatagramSocket, SocketAddress => JSocketAddress, SocketOption }
import java.nio.channels.{ DatagramChannel => JDatagramChannel }

/**
 * A [[java.nio.channels.DatagramChannel]] wrapper allowing for idiomatic [[zio.ZIO]] interoperability.
 */
final class DatagramChannel private[channels] (override protected[channels] val channel: JDatagramChannel)
    extends GatheringByteChannel
    with ScatteringByteChannel {

  private def bind(local: Option[SocketAddress]): IO[IOException, DatagramChannel] = {
    val addr: JSocketAddress = local.map(_.jSocketAddress).orNull
    IO.effect(channel.bind(addr)).as(this).refineToOrDie[IOException]
  }

  private def connect(remote: SocketAddress): IO[IOException, DatagramChannel] =
    IO.effect(channel.connect(remote.jSocketAddress)).as(this).refineToOrDie[IOException]

  /**
   * Disconnects this channel's underlying socket.
   *
   * @return the disconnected datagram channel
   */
  def disconnect: IO[IOException, DatagramChannel] =
    IO.effect(new DatagramChannel(channel.disconnect())).refineToOrDie[IOException]

  /**
   * Tells whether this channel's underlying socket is both open and connected.
   *
   * @return `true` when the socket is both open and connected, otherwise `false`
   */
  def isConnected: UIO[Boolean] = UIO.effectTotal(channel.isConnected())

  /**
   * Optionally returns the socket address that this channel's underlying socket is bound to.
   *
   * @return the local address if the socket is bound, otherwise `None`
   */
  def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getLocalAddress()).map(SocketAddress.fromJava)).refineToOrDie[IOException]

  /**
   * Receives a datagram via this channel into the given [[zio.nio.core.ByteBuffer]].
   *
   * @param dst the destination buffer
   * @return the socket address of the datagram's source, if available.
   */
  def receive(dst: ByteBuffer): IO[IOException, Option[SocketAddress]] =
    IO.effect(channel.receive(dst.byteBuffer))
      .refineToOrDie[IOException]
      .map(a => Option(a).map(SocketAddress.fromJava))

  /**
   * Optionally returns the remote socket address that this channel's underlying socket is connected to.
   *
   * @return the remote address if the socket is connected, otherwise `None`
   */
  def remoteAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getRemoteAddress()).map(SocketAddress.fromJava)).refineToOrDie[IOException]

  /**
   * Sends a datagram via this channel to the given target [[zio.nio.core.SocketAddress]].
   *
   * @param src the source buffer
   * @param target the target address
   * @return the number of bytes that were sent over this channel
   */
  def send(src: ByteBuffer, target: SocketAddress): IO[IOException, Int] =
    IO.effect(channel.send(src.byteBuffer, target.jSocketAddress)).refineToOrDie[IOException]

  /**
   * Sets the value of the given socket option.
   *
   * @param name the socket option to be set
   * @param value the value to be set
   * @return the datagram channel with the given socket option set to the provided value
   */
  def setOption[T](name: SocketOption[T], value: T): IO[IOException, DatagramChannel] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].map(new DatagramChannel(_))

  /**
   * Returns a reference to this channel's underlying datagram socket.
   *
   * @return the underlying datagram socket
   */
  def socket: UIO[JDatagramSocket] = IO.effectTotal(channel.socket())

  /**
   * Returns the set of operations supported by this channel.
   *
   * @return the set of valid operations
   */
  def validOps: UIO[Int] = UIO.effectTotal(channel.validOps())

}

object DatagramChannel {

  /**
   * Opens a datagram channel bound to the given local address as a managed resource.
   * Passing `None` binds to an automatically assigned local address.
   *
   * @param local the local address
   * @return a datagram channel bound to the local address
   */
  def bind(local: Option[SocketAddress]): Managed[IOException, DatagramChannel] = open.flatMap(_.bind(local).toManaged_)

  /**
   * Opens a datagram channel bound to the given local address as a managed resource.
   *
   * @param local the local address
   * @return a datagram channel bound to the local address
   */
  def bindTo(local: SocketAddress): Managed[IOException, DatagramChannel] = open.flatMap(_.bind(Some(local)).toManaged_)

  /**
   * Opens a datagram channel bound to an automatically assigned local address as a managed resource.
   *
   * @return a datagram channel bound to the local address
   */
  def bindAuto: Managed[IOException, DatagramChannel] = open.flatMap(_.bind(None).toManaged_)

  /**
   * Opens a datagram channel connected to the given remote address as a managed resource.
   *
   * @param remote the remote address
   * @return a datagram channel connected to the remote address
   */
  def connect(remote: SocketAddress): Managed[IOException, DatagramChannel] = open.flatMap(_.connect(remote).toManaged_)

  /**
   * Opens a new datagram channel as a managed resource. The channel will be
   * closed after use.
   *
   * @return a new datagram channel
   */
  private def open: Managed[IOException, DatagramChannel] = {
    val open = IO
      .effect(JDatagramChannel.open())
      .refineToOrDie[IOException]
      .map(new DatagramChannel(_))

    Managed.make(open)(_.close.orDie)
  }
}
