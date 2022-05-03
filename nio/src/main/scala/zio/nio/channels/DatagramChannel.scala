package zio.nio
package channels

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Scope, Trace, UIO, ZIO}

import java.io.IOException
import java.net.{DatagramSocket => JDatagramSocket, ProtocolFamily, SocketAddress => JSocketAddress, SocketOption}
import java.nio.channels.{DatagramChannel => JDatagramChannel}

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
     * @param remote
     *   the remote address
     */
    def connect(remote: SocketAddress)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.attempt(new DatagramChannel(self.channel.connect(remote.jSocketAddress))).unit.refineToOrDie[IOException]

    /**
     * Sends a datagram via this channel to the given target [[zio.nio.SocketAddress]].
     *
     * @param src
     *   the source buffer
     * @param target
     *   the target address
     * @return
     *   the number of bytes that were sent over this channel
     */
    def send(src: ByteBuffer, target: SocketAddress)(implicit trace: Trace): IO[IOException, Int] =
      ZIO.attempt(self.channel.send(src.buffer, target.jSocketAddress)).refineToOrDie[IOException]

  }

  final class BlockingDatagramOps private[DatagramChannel] () extends DatagramOps {

    /**
     * Receives a datagram via this channel into the given [[zio.nio.ByteBuffer]].
     *
     * @param dst
     *   the destination buffer
     * @return
     *   the socket address of the datagram's source, if available.
     */
    def receive(dst: ByteBuffer)(implicit trace: Trace): IO[IOException, SocketAddress] =
      ZIO
        .attempt(SocketAddress.fromJava(self.channel.receive(dst.buffer)))
        .refineToOrDie[IOException]

  }

  override protected def makeBlockingOps: BlockingDatagramOps = new BlockingDatagramOps

  final class NonBlockingDatagramOps private[DatagramChannel] () extends DatagramOps {

    /**
     * Receives a datagram via this channel into the given [[zio.nio.ByteBuffer]].
     *
     * @param dst
     *   the destination buffer
     * @return
     *   the socket address of the datagram's source, if available.
     */
    def receive(dst: ByteBuffer)(implicit trace: Trace): IO[IOException, Option[SocketAddress]] =
      ZIO.attempt(Option(self.channel.receive(dst.buffer)).map(SocketAddress.fromJava)).refineToOrDie[IOException]

  }

  override protected def makeNonBlockingOps: NonBlockingDatagramOps = new NonBlockingDatagramOps

  def bindTo(local: SocketAddress)(implicit trace: Trace): IO[IOException, Unit] = bind(Some(local))

  def bindAuto(implicit trace: Trace): IO[IOException, Unit] = bind(None)

  /**
   * Binds this channel's underlying socket to the given local address. Passing `None` binds to an automatically
   * assigned local address.
   *
   * @param local
   *   the local address
   * @return
   *   the datagram channel bound to the local address
   */
  def bind(local: Option[SocketAddress])(implicit trace: Trace): IO[IOException, Unit] = {
    val addr: JSocketAddress = local.map(_.jSocketAddress).orNull
    ZIO.attempt(self.channel.bind(addr)).refineToOrDie[IOException].unit
  }

  /**
   * Disconnects this channel's underlying socket.
   */
  def disconnect(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(new DatagramChannel(self.channel.disconnect())).unit.refineToOrDie[IOException]

  /**
   * Tells whether this channel's underlying socket is both open and connected.
   *
   * @return
   *   `true` when the socket is both open and connected, otherwise `false`
   */
  def isConnected(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(self.channel.isConnected())

  /**
   * Optionally returns the socket address that this channel's underlying socket is bound to.
   *
   * @return
   *   the local address if the socket is bound, otherwise `None`
   */
  def localAddress(implicit trace: Trace): IO[IOException, Option[SocketAddress]] =
    ZIO.attempt(Option(self.channel.getLocalAddress()).map(SocketAddress.fromJava)).refineToOrDie[IOException]

  /**
   * Optionally returns the remote socket address that this channel's underlying socket is connected to.
   *
   * @return
   *   the remote address if the socket is connected, otherwise `None`
   */
  def remoteAddress(implicit trace: Trace): IO[IOException, Option[SocketAddress]] =
    ZIO.attempt(Option(self.channel.getRemoteAddress()).map(SocketAddress.fromJava)).refineToOrDie[IOException]

  /**
   * Sets the value of the given socket option.
   *
   * @param name
   *   the socket option to be set
   * @param value
   *   the value to be set
   */
  def setOption[T](name: SocketOption[T], value: T)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(self.channel.setOption(name, value)).refineToOrDie[IOException].unit

  /**
   * Returns a reference to this channel's underlying datagram socket.
   *
   * @return
   *   the underlying datagram socket
   */
  def socket(implicit trace: Trace): UIO[JDatagramSocket] = ZIO.succeed(self.channel.socket())

}

object DatagramChannel {

  /**
   * Opens a new datagram channel.
   *
   * @return
   *   a new datagram channel
   */
  def open(implicit trace: Trace): ZIO[Scope, IOException, DatagramChannel] =
    ZIO
      .attempt(new DatagramChannel(JDatagramChannel.open()))
      .refineToOrDie[IOException]
      .toNioScoped

  def open(family: ProtocolFamily)(implicit trace: Trace): ZIO[Scope, IOException, DatagramChannel] =
    ZIO.attempt {
      val javaChannel = JDatagramChannel.open(family)
      javaChannel.configureBlocking(false)
      fromJava(javaChannel)
    }.refineToOrDie[IOException].toNioScoped

  def fromJava(javaDatagramChannel: JDatagramChannel): DatagramChannel = new DatagramChannel(javaDatagramChannel)

}
