package zio.nio.core.channels

import java.io.IOException
import java.net.{ SocketOption, DatagramSocket => JDatagramSocket, SocketAddress => JSocketAddress }
import java.nio.channels.{ DatagramChannel => JDatagramChannel }

import zio.{ IO, UIO }
import zio.nio.core.{ ByteBuffer, SocketAddress }

/**
 * A [[java.nio.channels.DatagramChannel]] wrapper allowing for basic [[zio.ZIO]] interoperability.
 */
final class DatagramChannel private[channels] (override protected[channels] val channel: JDatagramChannel)
    extends GatheringByteChannel
    with ScatteringByteChannel {

  /**
   * Binds this channel's underlying socket to the given local address. Passing `None` binds to an
   * automatically assigned local address.
   *
   * @param local the local address
   * @return the datagram channel bound to the local address
   */
  def bind(local: Option[SocketAddress]): IO[IOException, DatagramChannel] = {
    val addr: JSocketAddress = local.map(_.jSocketAddress).orNull
    IO.effect(new DatagramChannel(channel.bind(addr))).refineToOrDie[IOException]
  }

  /**
   * Connects this channel's underlying socket to the given remote address.
   *
   * @param remote the remote address
   * @return the datagram channel connected to the remote address
   */
  def connect(remote: SocketAddress): IO[IOException, DatagramChannel] =
    IO.effect(new DatagramChannel(channel.connect(remote.jSocketAddress))).refineToOrDie[IOException]

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
  def isConnected: UIO[Boolean] =
    UIO.effectTotal(channel.isConnected())

  /**
   * Optionally returns the socket address that this channel's underlying socket is bound to.
   *
   * @return the local address if the socket is bound, otherwise `None`
   */
  def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(channel.getLocalAddress()).refineToOrDie[IOException].map(a => Option(a).map(new SocketAddress(_)))

  /**
   * Reads a datagram into the given [[zio.nio.core.ByteBuffer]]. This effect can only succeed
   * if the channel is connected, and it only accepts datagrams from the connected remote address.
   *
   * @param dst the destination buffer
   * @return the number of bytes that were read from this channel
   */
  def read(dst: ByteBuffer): IO[IOException, Int] =
    IO.effect(channel.read(dst.byteBuffer)).refineToOrDie[IOException]

  /**
   * Receives a datagram via this channel into the given [[zio.nio.core.ByteBuffer]].
   *
   * @param dst the destination buffer
   * @return the socket address of the datagram's source, if available.
   */
  def receive(dst: ByteBuffer): IO[IOException, Option[SocketAddress]] =
    IO.effect(channel.receive(dst.byteBuffer)).refineToOrDie[IOException].map(a => Option(a).map(new SocketAddress(_)))

  /**
   * Optionally returns the remote socket address that this channel's underlying socket is connected to.
   *
   * @return the remote address if the socket is connected, otherwise `None`
   */
  def remoteAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(channel.getRemoteAddress()).refineToOrDie[IOException].map(a => Option(a).map(new SocketAddress(_)))

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
  def socket: UIO[JDatagramSocket] =
    IO.effectTotal(channel.socket())

  /**
   * Returns the set of operations supported by this channel.
   *
   * @return the set of valid operations
   */
  def validOps: UIO[Int] =
    UIO.effectTotal(channel.validOps())

  /**
   * Writes a datagram read from the given [[zio.nio.core.ByteBuffer]]. This effect can only succeed
   * if the channel is connected, and it only sends datagrams to the connected remote address.
   *
   * @param src the source buffer from which the datagram is to be read
   * @return the number of bytes that were written to this channel
   */
  def write(src: ByteBuffer): IO[IOException, Int] =
    IO.effect(channel.write(src.byteBuffer)).refineToOrDie[IOException]
}

object DatagramChannel {

  /**
   * Opens a new datagram channel.
   *
   * @return a new datagram channel
   */
  def open: IO[Exception, DatagramChannel] =
    IO.effect(JDatagramChannel.open())
      .refineToOrDie[Exception]
      .map(new DatagramChannel(_))
}
