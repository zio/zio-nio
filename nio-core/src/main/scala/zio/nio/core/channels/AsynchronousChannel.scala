package zio.nio.core.channels

import java.io.IOException
import java.lang.{ Integer => JInteger, Long => JLong, Void => JVoid }
import java.net.SocketOption
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel
}
import java.util.concurrent.TimeUnit

import zio.{ Chunk, IO, Schedule, ZIO }
import zio.duration._
import zio.interop.javaz._
import zio.nio.core.{ Buffer, ByteBuffer, RichInt, RichLong, SocketAddress }

/**
 * A byte channel that reads and writes asynchronously.
 *
 * The read and write operations will never block the calling thread.
 */
abstract class AsynchronousByteChannel private[channels] (protected val channel: JAsynchronousByteChannel)
    extends Channel {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final def read(b: ByteBuffer): IO[Exception, Option[Int]] =
    effectAsyncWithCompletionHandler[JInteger](h => channel.read(b.byteBuffer, (), h))
      .map(_.toInt.eofCheck)
      .refineToOrDie[Exception]

  final def readChunk(capacity: Int): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      l <- read(b)
      _ <- b.flip
      r <- l.map(_ => b.getChunk()).getOrElse(ZIO.fail(new IOException("Connection reset by peer")))
    } yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write(b: ByteBuffer): IO[Exception, Int] =
    effectAsyncWithCompletionHandler[JInteger](h => channel.write(b.byteBuffer, (), h))
      .map(_.toInt)
      .refineToOrDie[Exception]

  /**
   * Writes a chunk of bytes to this channel.
   *
   * More than one write operation may be performed to write out the entire chunk.
   */
  final def writeChunk(chunk: Chunk[Byte]): IO[Exception, Unit] =
    for {
      b <- Buffer.byte(chunk)
      _ <- write(b).repeat(Schedule.doWhileM(_ => b.hasRemaining))
    } yield ()

}

final class AsynchronousServerSocketChannel(protected val channel: JAsynchronousServerSocketChannel) extends Channel {

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections.
   */
  def bind(address: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(address.jSocketAddress)).refineToOrDie[IOException].unit

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  def bind(address: SocketAddress, backlog: Int): IO[IOException, Unit] =
    IO.effect(channel.bind(address.jSocketAddress, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  /**
   * Accepts a connection.
   */
  val accept: IO[Exception, AsynchronousSocketChannel] =
    effectAsyncWithCompletionHandler[JAsynchronousSocketChannel](h => channel.accept((), h))
      .map(AsynchronousSocketChannel.fromJava)
      .refineToOrDie[Exception]

  /**
   * The `SocketAddress` that the socket is bound to,
   * or the `SocketAddress` representing the loopback address if
   * denied by the security manager, or `Maybe.empty` if the
   * channel's socket is not bound.
   */
  def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getLocalAddress).map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

}

object AsynchronousServerSocketChannel {

  def apply(): IO[IOException, AsynchronousServerSocketChannel] =
    IO.effect(new AsynchronousServerSocketChannel(JAsynchronousServerSocketChannel.open()))
      .refineToOrDie[IOException]

  def apply(
    channelGroup: AsynchronousChannelGroup
  ): IO[IOException, AsynchronousServerSocketChannel] =
    IO.effect(new AsynchronousServerSocketChannel(JAsynchronousServerSocketChannel.open(channelGroup.channelGroup)))
      .refineToOrDie[IOException]
}

class AsynchronousSocketChannel(override protected val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  final def bind(address: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(address.jSocketAddress)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  final def shutdownInput: IO[IOException, Unit] =
    IO.effect(channel.shutdownInput()).refineToOrDie[IOException].unit

  final def shutdownOutput: IO[IOException, Unit] =
    IO.effect(channel.shutdownOutput()).refineToOrDie[IOException].unit

  final def remoteAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getRemoteAddress)
        .map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

  final def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getLocalAddress)
        .map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

  final def connect(socketAddress: SocketAddress): IO[Exception, Unit] =
    effectAsyncWithCompletionHandler[JVoid](h => channel.connect(socketAddress.jSocketAddress, (), h)).unit
      .refineToOrDie[Exception]

  final def read[A](dst: ByteBuffer, timeout: Duration): IO[Exception, Option[Int]] =
    effectAsyncWithCompletionHandler[JInteger] { h =>
      channel.read(
        dst.byteBuffer,
        timeout.fold(Long.MaxValue, _.nanos),
        TimeUnit.NANOSECONDS,
        (),
        h
      )
    }.map(_.toInt.eofCheck).refineToOrDie[Exception]

  final def readChunk[A](capacity: Int, timeout: Duration): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      l <- read(b, timeout)
      _ <- b.flip
      r <- l.map(_ => b.getChunk()).getOrElse(ZIO.fail(new IOException("Connection reset by peer")))
    } yield r

  final def read[A](
    dsts: List[ByteBuffer],
    timeout: Duration
  ): IO[Exception, Option[Long]] =
    effectAsyncWithCompletionHandler[JLong] { h =>
      val a = dsts.map(_.byteBuffer).toArray
      channel.read(
        a,
        0,
        a.length,
        timeout.fold(Long.MaxValue, _.nanos),
        TimeUnit.NANOSECONDS,
        (),
        h
      )
    }.map(_.toLong.eofCheck).refineToOrDie[Exception]

  final def readChunks[A](
    capacities: List[Int],
    timeout: Duration
  ): IO[Exception, List[Chunk[Byte]]] =
    for {
      bs     <- ZIO.foreach(capacities)(Buffer.byte)
      l      <- read(bs, timeout)
      chunks <- ZIO.foreach(bs)(b => b.flip *> b.getChunk())
      r      <- l.map(_ => ZIO.succeed(chunks)).getOrElse(ZIO.fail(new IOException("Connection reset by peer")))
    } yield r
}

object AsynchronousSocketChannel {

  def apply(): IO[IOException, AsynchronousSocketChannel] =
    IO.effect(new AsynchronousSocketChannel(JAsynchronousSocketChannel.open()))
      .refineToOrDie[IOException]

  def apply(channelGroup: AsynchronousChannelGroup): IO[IOException, AsynchronousSocketChannel] =
    IO.effect(new AsynchronousSocketChannel(JAsynchronousSocketChannel.open(channelGroup.channelGroup)))
      .refineToOrDie[IOException]

  def fromJava(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)

}
