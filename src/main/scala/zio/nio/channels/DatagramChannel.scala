package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ DatagramChannel => JDatagramChannel }
import java.net.{ DatagramSocket => JDatagramSocket }

import zio.nio.{ ByteBuffer, SocketAddress }
import zio.{ IO, Managed, UIO }

final class DatagramChannel private[channels] (override protected[channels] val channel: JDatagramChannel)
    extends GatheringByteChannel
    with ScatteringByteChannel {

  def bind(local: SocketAddress): IO[IOException, DatagramChannel] =
    IO.effect(new DatagramChannel(channel.bind(local.jSocketAddress))).refineToOrDie[IOException]

  def connect(remote: SocketAddress): IO[IOException, DatagramChannel] =
    IO.effect(new DatagramChannel(channel.connect(remote.jSocketAddress))).refineToOrDie[IOException]

  def socket(): UIO[JDatagramSocket] =
    IO.effectTotal(channel.socket())

  def receive(dst: ByteBuffer): IO[IOException, SocketAddress] =
    IO.effect(new SocketAddress(channel.receive(dst.byteBuffer))).refineToOrDie[IOException]

  def send(src: ByteBuffer, target: SocketAddress): IO[IOException, Int] =
    IO.effect(channel.send(src.byteBuffer, target.jSocketAddress)).refineToOrDie[IOException]

  def read(dst: ByteBuffer): IO[IOException, Int] =
    IO.effect(channel.read(dst.byteBuffer)).refineToOrDie[IOException]

  def write(src: ByteBuffer): IO[IOException, Int] =
    IO.effect(channel.write(src.byteBuffer)).refineToOrDie[IOException]
}

object DatagramChannel {

  def apply(): Managed[Exception, DatagramChannel] = {
    val open = IO
      .effect(JDatagramChannel.open())
      .refineToOrDie[Exception]
      .map(new DatagramChannel(_))

    Managed.make(open)(_.close.orDie)
  }
}
