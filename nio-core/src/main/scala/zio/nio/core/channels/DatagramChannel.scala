package zio.nio.core
package channels

import java.io.IOException
import java.net.{ ProtocolFamily, SocketOption, DatagramSocket => JDatagramSocket, SocketAddress => JSocketAddress }
import java.nio.channels.{ DatagramChannel => JDatagramChannel }

import zio.{ IO, Managed, UIO }

/**
 * A [[java.nio.channels.DatagramChannel]] wrapper allowing for basic [[zio.ZIO]] interoperability.
 */
final class DatagramChannel private[channels] (override protected val channel: JDatagramChannel)
    extends SelectableChannel {

  self =>

  override type BlockingOps = BlockingDatagramOps

  override type NonBlockingOps = NonBlockingDatagramOps

  sealed abstract class DatagramOps extends GatheringByteOps with ScatteringByteOps {

    override protected[channels] def channel = self.channel

    /**
     * Connects this channel's underlying socket to the given remote address.
     *
     * @param remote the remote address
     */
    def connect(remote: SocketAddress): IO[IOException, Unit] =
      IO.effect(new DatagramChannel(channel.connect(remote.jSocketAddress))).unit.refineToOrDie[IOException]

    /**
     * Sends a datagram via this channel to the given target [[zio.nio.core.SocketAddress]].
     *
     * @param src    the source buffer
     * @param target the target address
     * @return the number of bytes that were sent over this channel
     */
    def send(src: ByteBuffer, target: SocketAddress): IO[IOException, Int] =
      IO.effect(channel.send(src.byteBuffer, target.jSocketAddress)).refineToOrDie[IOException]

  }

  final class BlockingDatagramOps private[DatagramChannel] () extends DatagramOps {

    /**
     * Receives a datagram via this channel into the given [[zio.nio.core.ByteBuffer]].
     *
     * @param dst the destination buffer
     * @return the socket address of the datagram's source, if available.
     */
    def receive(dst: ByteBuffer): IO[IOException, SocketAddress] =
      IO.effect(new SocketAddress(channel.receive(dst.byteBuffer)))
        .refineToOrDie[IOException]

  }

  override protected def makeBlockingOps: BlockingDatagramOps = new BlockingDatagramOps

  final class NonBlockingDatagramOps private[DatagramChannel] () extends DatagramOps {

    /**
     * Receives a datagram via this channel into the given [[zio.nio.core.ByteBuffer]].
     *
     * @param dst the destination buffer
     * @return the socket address of the datagram's source, if available.
     */
    def receive(dst: ByteBuffer): IO[IOException, Option[SocketAddress]] =
      IO.effect(Option(channel.receive(dst.byteBuffer)).map(new SocketAddress(_))).refineToOrDie[IOException]

  }

  override protected def makeNonBlockingOps: NonBlockingDatagramOps = new NonBlockingDatagramOps

  /**
   * Binds this channel's underlying socket to the given local address. Passing `None` binds to an
   * automatically assigned local address.
   *
   * @param local the local address
   * @return the datagram channel bound to the local address
   */
  def bind(local: Option[SocketAddress]): IO[IOException, Unit] = {
    val addr: JSocketAddress = local.map(_.jSocketAddress).orNull
    IO.effect(channel.bind(addr)).refineToOrDie[IOException].unit
  }

  /**
   * Disconnects this channel's underlying socket.
   */
  def disconnect: IO[IOException, Unit] =
    IO.effect(new DatagramChannel(channel.disconnect())).unit.refineToOrDie[IOException]

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
   * Optionally returns the remote socket address that this channel's underlying socket is connected to.
   *
   * @return the remote address if the socket is connected, otherwise `None`
   */
  def remoteAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(channel.getRemoteAddress()).refineToOrDie[IOException].map(a => Option(a).map(new SocketAddress(_)))

  /**
   * Sets the value of the given socket option.
   *
   * @param name the socket option to be set
   * @param value the value to be set
   */
  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  /**
   * Returns a reference to this channel's underlying datagram socket.
   *
   * @return the underlying datagram socket
   */
  def socket: UIO[JDatagramSocket] =
    IO.effectTotal(channel.socket())

}

object DatagramChannel {

  /**
   * Opens a new datagram channel.
   *
   * @return a new datagram channel
   */
  def open: Managed[IOException, DatagramChannel] =
    IO.effect(new DatagramChannel(JDatagramChannel.open()))
      .refineToOrDie[IOException]
      .toNioManaged

  def open(family: ProtocolFamily): Managed[IOException, DatagramChannel] =
    IO.effect {
      val javaChannel = JDatagramChannel.open(family)
      javaChannel.configureBlocking(false)
      fromJava(javaChannel)
    }.refineToOrDie[IOException]
      .toNioManaged

  def fromJava(javaDatagramChannel: JDatagramChannel): DatagramChannel =
    new DatagramChannel(javaDatagramChannel)

}
