package zio.nio.channels

import java.io.IOException
import java.lang.{ Integer => JInteger, Long => JLong, Void => JVoid }
import java.net.SocketOption
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel
}
import java.util.concurrent.TimeUnit

import zio._
import zio.duration._
import zio.interop.javaz._
import zio.nio.core.{ Buffer, ByteBuffer, SocketAddress, eofCheck }
import zio.nio.core.channels.AsynchronousChannelGroup

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   */
  final def read(b: ByteBuffer): IO[Exception, Int] =
    effectAsyncWithCompletionHandler[JInteger](h => channel.read(b.byteBuffer, (), h))
      .refineToOrDie[Exception]
      .flatMap(eofCheck(_))

  final def readChunk(capacity: Int): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- read(b)
      _ <- b.flip
      r <- b.getChunk()
    } yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write(b: ByteBuffer): IO[Exception, Int] =
    effectAsyncWithCompletionHandler[JInteger](h => channel.write(b.byteBuffer, (), h))
      .map(_.toInt)
      .refineToOrDie[Exception]

  final def writeChunk(chunk: Chunk[Byte]): IO[Exception, Int] =
    for {
      b <- Buffer.byte(chunk)
      r <- write(b)
    } yield r

  /**
   * Closes this channel.
   */
  final private[channels] val close: IO[IOException, Unit] =
    IO.effect(channel.close()).refineToOrDie[IOException]

  /**
   * Tells whether or not this channel is open.
   */
  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)
}

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel) {

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  final def bindTo(local: SocketAddress, backlog: Int = 0): IO[IOException, Unit] = bind(Some(local), backlog)

  /**
   * Binds the channel's socket to an automatically assigned local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  final def bindAuto(backlog: Int = 0): IO[IOException, Unit] = bind(None, backlog)

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  final def bind(address: Option[SocketAddress], backlog: Int = 0): IO[IOException, Unit] =
    IO.effect(channel.bind(address.map(_.jSocketAddress).orNull, backlog)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  /**
   * Accepts a connection.
   */
  final val accept: Managed[Exception, AsynchronousSocketChannel] =
    Managed
      .makeInterruptible(
        effectAsyncWithCompletionHandler[JAsynchronousSocketChannel](h => channel.accept((), h))
          .map(AsynchronousSocketChannel(_))
      )(_.close.orDie)
      .refineToOrDie[Exception]

  /**
   * The `SocketAddress` that the socket is bound to,
   * or the `SocketAddress` representing the loopback address if
   * denied by the security manager, or `Maybe.empty` if the
   * channel's socket is not bound.
   */
  final def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getLocalAddress).map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

  /**
   * Closes this channel.
   */
  final private[channels] val close: IO[IOException, Unit] =
    IO.effect(channel.close()).refineToOrDie[IOException]

  /**
   * Tells whether or not this channel is open.
   */
  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)
}

object AsynchronousServerSocketChannel {

  def apply(): Managed[IOException, AsynchronousServerSocketChannel] = {
    val open = IO
      .effect(new AsynchronousServerSocketChannel(JAsynchronousServerSocketChannel.open()))
      .refineToOrDie[IOException]

    Managed.make(open)(_.close.orDie)
  }

  def apply(
    channelGroup: AsynchronousChannelGroup
  ): Managed[IOException, AsynchronousServerSocketChannel] = {
    val open = IO
      .effect(
        new AsynchronousServerSocketChannel(JAsynchronousServerSocketChannel.open(channelGroup.channelGroup))
      )
      .refineToOrDie[IOException]

    Managed.make(open)(_.close.orDie)
  }
}

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  final def bind(address: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(address.jSocketAddress)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  final def shutdownInput: IO[IOException, Unit] = IO.effect(channel.shutdownInput()).refineToOrDie[IOException].unit

  final def shutdownOutput: IO[IOException, Unit] = IO.effect(channel.shutdownOutput()).refineToOrDie[IOException].unit

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

  /**
   *  Reads data from this channel into buffer, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   */
  final def read[A](dst: ByteBuffer, timeout: Duration): IO[Exception, Int] =
    effectAsyncWithCompletionHandler[JInteger] { h =>
      channel.read(
        dst.byteBuffer,
        timeout.fold(Long.MaxValue, _.toNanos),
        TimeUnit.NANOSECONDS,
        (),
        h
      )
    }
      .refineToOrDie[Exception]
      .flatMap(eofCheck(_))

  final def readChunk[A](capacity: Int, timeout: Duration): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- read(b, timeout)
      _ <- b.flip
      r <- b.getChunk()
    } yield r

  /**
   *  Reads data from this channel into a set of buffers, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   */
  final def read[A](
    dsts: List[ByteBuffer],
    timeout: Duration
  ): IO[Exception, Long] =
    effectAsyncWithCompletionHandler[JLong] { h =>
      val a = dsts.map(_.byteBuffer).toArray
      channel.read(
        dsts.map(_.byteBuffer).toArray,
        0,
        a.length,
        timeout.fold(Long.MaxValue, _.toNanos),
        TimeUnit.NANOSECONDS,
        (),
        h
      )
    }
      .refineToOrDie[Exception]
      .flatMap(eofCheck(_))

  final def readChunks[A](
    capacities: List[Int],
    timeout: Duration
  ): IO[Exception, List[Chunk[Byte]]] =
    for {
      bs     <- ZIO.foreach(capacities)(Buffer.byte)
      _      <- read(bs, timeout)
      chunks <- ZIO.foreach(bs)(b => b.flip *> b.getChunk())
    } yield chunks
}

object AsynchronousSocketChannel {

  def apply(): Managed[IOException, AsynchronousSocketChannel] = {
    val open = IO
      .effect(new AsynchronousSocketChannel(JAsynchronousSocketChannel.open()))
      .refineToOrDie[IOException]

    Managed.make(open)(_.close.orDie)
  }

  def apply(channelGroup: AsynchronousChannelGroup): Managed[IOException, AsynchronousSocketChannel] = {
    val open = IO
      .effect(
        new AsynchronousSocketChannel(JAsynchronousSocketChannel.open(channelGroup.channelGroup))
      )
      .refineToOrDie[IOException]

    Managed.make(open)(_.close.orDie)
  }

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)
}
