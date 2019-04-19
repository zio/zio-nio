package scalaz.nio.channels

import java.lang.{ Integer => JInteger, Long => JLong, Void => JVoid }
import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel,
  CompletionHandler => JCompletionHandler
}
import java.util.concurrent.TimeUnit

import scalaz.nio.channels.AsynchronousChannel._
import scalaz.nio.{ Buffer, SocketAddress, SocketOption }
import scalaz.zio.{ Chunk, IO, JustExceptions, UIO, ZIO }
import scalaz.zio.duration._

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final private[nio] def readBuffer(b: Buffer[Byte]): IO[Exception, Int] =
    wrap[Unit, JInteger](h => channel.read(b.buffer.asInstanceOf[JByteBuffer], (), h)).map(_.toInt)

  final def read(capacity: Int): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- readBuffer(b)
      a <- b.array
      r = Chunk.fromArray(a)
    } yield r

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final private[nio] def readBuffer[A](b: Buffer[Byte], attachment: A): IO[Exception, Int] =
    wrap[A, JInteger](h => channel.read(b.buffer.asInstanceOf[JByteBuffer], attachment, h))
      .map(_.toInt)

  final def read[A](capacity: Int, attachment: A): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- readBuffer(b, attachment)
      a <- b.array
      r = Chunk.fromArray(a)
    } yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final private[nio] def writeBuffer(b: Buffer[Byte]): IO[Exception, Int] =
    wrap[Unit, JInteger](h => channel.write(b.buffer.asInstanceOf[JByteBuffer], (), h)).map(_.toInt)

  final def write(chunk: Chunk[Byte]): IO[Exception, Int] =
    for {
      b <- Buffer.byte(chunk)
      r <- writeBuffer(b)
    } yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final private[nio] def write[A](b: Buffer[Byte], attachment: A): IO[Exception, Int] =
    wrap[A, JInteger](h => channel.write(b.buffer.asInstanceOf[JByteBuffer], attachment, h))
      .map(_.toInt)

  final def write[A](chunk: Chunk[Byte], attachment: A): IO[Exception, Int] =
    for {
      b <- Buffer.byte(chunk)
      r <- write(b, attachment)
    } yield r

  /**
   * Closes this channel.
   */
  final val close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineOrDie(JustExceptions)

  /**
   * Tells whether or not this channel is open.
   */
  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)
}

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel) {

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections.
   */
  final def bind(address: SocketAddress): IO[Exception, Unit] =
    IO.effect(channel.bind(address.jSocketAddress)).refineOrDie(JustExceptions).void

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  final def bind(address: SocketAddress, backlog: Int): IO[Exception, Unit] =
    IO.effect(channel.bind(address.jSocketAddress, backlog)).refineOrDie(JustExceptions).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.effect(channel.setOption(name.jSocketOption, value)).refineOrDie(JustExceptions).void

  /**
   * Accepts a connection.
   */
  final val accept: IO[Exception, AsynchronousSocketChannel] =
    wrap[Unit, JAsynchronousSocketChannel](h => channel.accept((), h))
      .map(AsynchronousSocketChannel(_))

  /**
   * Accepts a connection.
   */
  final def accept[A](attachment: A): IO[Exception, AsynchronousSocketChannel] =
    wrap[A, JAsynchronousSocketChannel](h => channel.accept(attachment, h))
      .map(AsynchronousSocketChannel(_))

  /**
   * The `SocketAddress` that the socket is bound to,
   * or the `SocketAddress` representing the loopback address if
   * denied by the security manager, or `Maybe.empty` if the
   * channel's socket is not bound.
   */
  final def localAddress: IO[Exception, Option[SocketAddress]] =
    IO.effect(
        Option(channel.getLocalAddress).map(new SocketAddress(_))
      )
      .refineOrDie(JustExceptions)

  /**
   * Closes this channel.
   */
  final val close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineOrDie(JustExceptions)

  /**
   * Tells whether or not this channel is open.
   */
  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)
}

object AsynchronousServerSocketChannel {

  def apply(): IO[Exception, AsynchronousServerSocketChannel] =
    IO.effect(JAsynchronousServerSocketChannel.open())
      .refineOrDie(JustExceptions)
      .map(new AsynchronousServerSocketChannel(_))

  def apply(
    channelGroup: AsynchronousChannelGroup
  ): IO[Exception, AsynchronousServerSocketChannel] =
    IO.effect(
        JAsynchronousServerSocketChannel.open(channelGroup.channelGroup)
      )
      .refineOrDie {
        case e: Exception => e
      }
      .map(new AsynchronousServerSocketChannel(_))
}

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  final def bind(address: SocketAddress): IO[Exception, Unit] =
    IO.effect(channel.bind(address.jSocketAddress)).refineOrDie(JustExceptions).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.effect(channel.setOption(name.jSocketOption, value)).refineOrDie(JustExceptions).void

  final def shutdownInput: IO[Exception, Unit] =
    IO.effect(channel.shutdownInput()).refineOrDie(JustExceptions).void

  final def shutdownOutput: IO[Exception, Unit] =
    IO.effect(channel.shutdownOutput()).refineOrDie(JustExceptions).void

  final def remoteAddress: IO[Exception, Option[SocketAddress]] =
    IO.effect(
        Option(channel.getRemoteAddress)
          .map(new SocketAddress(_))
      )
      .refineOrDie(JustExceptions)

  final def localAddress: IO[Exception, Option[SocketAddress]] =
    IO.effect(
        Option(channel.getLocalAddress)
          .map(new SocketAddress(_))
      )
      .refineOrDie(JustExceptions)

  final def connect(socketAddress: SocketAddress): IO[Exception, Unit] =
    wrap[Unit, JVoid](h => channel.connect(socketAddress.jSocketAddress, (), h)).void

  final def connect[A](socketAddress: SocketAddress, attachment: A): IO[Exception, Unit] =
    wrap[A, JVoid](h => channel.connect(socketAddress.jSocketAddress, attachment, h)).void

  final private[nio] def readBuffer[A](
    dst: Buffer[Byte],
    timeout: Duration,
    attachment: A
  ): IO[Exception, Int] =
    wrap[A, JInteger] { h =>
      channel.read(
        dst.buffer.asInstanceOf[JByteBuffer],
        timeout.fold(Long.MaxValue, _.nanos),
        TimeUnit.NANOSECONDS,
        attachment,
        h
      )
    }.map(_.toInt)

  final def read[A](capacity: Int, timeout: Duration, attachment: A): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- readBuffer(b, timeout, attachment)
      a <- b.array
      r = Chunk.fromArray(a)
    } yield r

  final private[nio] def readBuffer[A](
    dsts: List[Buffer[Byte]],
    offset: Int,
    length: Int,
    timeout: Duration,
    attachment: A
  ): IO[Exception, Long] =
    wrap[A, JLong](
      h =>
        channel.read(
          dsts.map(_.buffer.asInstanceOf[JByteBuffer]).toList.toArray,
          offset,
          length,
          timeout.fold(Long.MaxValue, _.nanos),
          TimeUnit.NANOSECONDS,
          attachment,
          h
        )
    ).map(_.toLong)

  final def read[A](
    capacities: List[Int],
    offset: Int,
    length: Int,
    timeout: Duration,
    attachment: A
  ): IO[Exception, List[Chunk[Byte]]] =
    for {
      bs <- IO.collectAll(capacities.map(Buffer.byte(_)))
      _  <- readBuffer(bs, offset, length, timeout, attachment)
      as <- IO.collectAll(bs.map(_.array))
      ds = as.map(Chunk.fromArray(_))
    } yield ds

}

object AsynchronousSocketChannel {

  def apply(): IO[Exception, AsynchronousSocketChannel] =
    IO.effect(JAsynchronousSocketChannel.open())
      .refineOrDie(JustExceptions)
      .map(new AsynchronousSocketChannel(_))

  def apply(channelGroup: AsynchronousChannelGroup): IO[Exception, AsynchronousSocketChannel] =
    IO.effect(
        JAsynchronousSocketChannel.open(channelGroup.channelGroup)
      )
      .refineOrDie {
        case e: Exception => e
      }
      .map(new AsynchronousSocketChannel(_))

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)
}

object AsynchronousChannel {

  private[nio] def wrap[A, T](op: JCompletionHandler[T, A] => Unit): ZIO[Any, Exception, T] =
    ZIO.effectAsync[Exception, T] { k =>
      val handler = new JCompletionHandler[T, A] {
        def completed(result: T, u: A): Unit =
          k(IO.succeedLazy(result))

        def failed(t: Throwable, u: A): Unit =
          t match {
            case e: Exception => k(IO.fail(e))
            case _            => k(IO.die(t))
          }
      }

      try {
        op(handler)
      } catch {
        case e: Exception => k(IO.fail(e))
        case t: Throwable => k(IO.die(t))
      }
    }

}
