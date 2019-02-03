package scalaz.nio.channels

import java.lang.{ Integer => JInteger, Long => JLong, Void => JVoid }
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousChannelGroup => JAsynchronousChannelGroup,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel,
  CompletionHandler => JCompletionHandler
}
import java.util.concurrent.TimeUnit

import scalaz.nio.channels.AsynchronousChannel._
import scalaz.nio.{ ByteBuffer, SocketAddress, SocketOption }
import scalaz.zio.duration._
import scalaz.zio.{ Async, IO }
import scalaz.{ IList, Maybe }

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final def read(b: ByteBuffer): IO[Exception, Int] =
    wrap[Unit, JInteger](h => channel.read(b.buffer, (), h)).map(_.toInt)

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final def read[A](b: ByteBuffer, attachment: A): IO[Exception, Int] =
    wrap[A, JInteger](h => channel.read(b.buffer, attachment, h)).map(_.toInt)

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write(b: ByteBuffer): IO[Exception, Int] =
    wrap[Unit, JInteger](h => channel.write(b.buffer, (), h)).map(_.toInt)

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write[A](b: ByteBuffer, attachment: A): IO[Exception, Int] =
    wrap[A, JInteger](h => channel.write(b.buffer, attachment, h)).map(_.toInt)

  /**
   * Closes this channel.
   */
  final def close: IO[Exception, Unit] =
    IO.syncException(channel.close())

}

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel) {

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections.
   */
  final def bind(address: SocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address.jSocketAddress)).void

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  final def bind(address: SocketAddress, backlog: Int): IO[Exception, Unit] =
    IO.syncException(channel.bind(address.jSocketAddress, backlog)).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name.jSocketOption, value)).void

  /**
   * Accepts a connection.
   */
  final def accept: IO[Exception, AsynchronousSocketChannel] =
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
  final def localAddress: IO[Exception, Maybe[SocketAddress]] =
    IO.syncException(
      Maybe
        .fromNullable(channel.getLocalAddress)
        .map(new SocketAddress(_))
    )

  /**
   * Closes this channel.
   */
  final def close: IO[Exception, Unit] =
    IO.syncException(channel.close())

}

object AsynchronousServerSocketChannel {

  def apply(): IO[Exception, AsynchronousServerSocketChannel] =
    IO.syncException(JAsynchronousServerSocketChannel.open())
      .map(new AsynchronousServerSocketChannel(_))

  def apply(
    channelGroup: AsynchronousChannelGroup
  ): IO[Exception, AsynchronousServerSocketChannel] =
    IO.syncException(
        JAsynchronousServerSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousServerSocketChannel(_))
}

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  final def bind(address: SocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address.jSocketAddress)).void

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.syncException(channel.setOption(name.jSocketOption, value)).void

  final def shutdownInput: IO[Exception, Unit] =
    IO.syncException(channel.shutdownInput()).void

  final def shutdownOutput: IO[Exception, Unit] =
    IO.syncException(channel.shutdownOutput()).void

  final def remoteAddress: IO[Exception, Maybe[SocketAddress]] =
    IO.syncException(
      Maybe
        .fromNullable(channel.getRemoteAddress)
        .map(new SocketAddress(_))
    )

  final def localAddress: IO[Exception, Maybe[SocketAddress]] =
    IO.syncException(
      Maybe
        .fromNullable(channel.getLocalAddress)
        .map(new SocketAddress(_))
    )

  final def connect[A](attachment: A, socketAddress: SocketAddress): IO[Exception, Unit] =
    wrap[A, JVoid](h => channel.connect(socketAddress.jSocketAddress, attachment, h)).void

  final def connect(socketAddress: SocketAddress): IO[Exception, Unit] =
    wrap[Unit, JVoid](h => channel.connect(socketAddress.jSocketAddress, (), h)).void

  def read[A](dst: ByteBuffer, timeout: Duration, attachment: A): IO[Exception, Int] =
    wrap[A, JInteger] { h =>
      channel.read(
        dst.buffer,
        timeout.fold(Long.MaxValue, _.nanos),
        TimeUnit.NANOSECONDS,
        attachment,
        h
      )
    }.map(_.toInt)

  def read[A](
    dsts: IList[ByteBuffer],
    offset: Int,
    length: Int,
    timeout: Duration,
    attachment: A
  ): IO[Exception, Long] =
    wrap[A, JLong](
      h =>
        channel.read(
          dsts.map(_.buffer).toList.toArray,
          offset,
          length,
          timeout.fold(Long.MaxValue, _.nanos),
          TimeUnit.NANOSECONDS,
          attachment,
          h
        )
    ).map(_.toLong)
}

object AsynchronousSocketChannel {

  def apply(): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(JAsynchronousSocketChannel.open())
      .map(new AsynchronousSocketChannel(_))

  def apply(channelGroup: AsynchronousChannelGroup): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(
        JAsynchronousSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousSocketChannel(_))

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)
}

class AsynchronousChannelGroup(val jChannelGroup: JAsynchronousChannelGroup) {}

object AsynchronousChannelGroup {

  def apply(): IO[Exception, AsynchronousChannelGroup] =
    ??? // IO.syncException { throw new Exception() }
}

object AsynchronousChannel {
  private[nio] def wrap[A, T](op: JCompletionHandler[T, A] => Unit): IO[Exception, T] =
    IO.async0[Exception, T] { k =>
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
        Async.later
      } catch {
        case e: Exception => Async.now(IO.fail(e))
        case t: Throwable => Async.now(IO.die(t))
      }
    }
}
