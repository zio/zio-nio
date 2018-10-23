package scalaz.nio.channels

import java.net.InetSocketAddress
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousChannelGroup => JAsynchronousChannelGroup,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel,
  CompletionHandler => JCompletionHandler
}
import java.nio.{ ByteBuffer => JByteBuffer }

import scalaz.nio.Buffer
import scalaz.zio.{ Async, ExitResult, IO }

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final def read(b: Buffer[Byte]): IO[Exception, Int] =
    IO.async0[Exception, Int] { k =>
      try {
        val byteBuffer = b.buffer.asInstanceOf[JByteBuffer]
        channel.read(
          byteBuffer,
          (),
          new JCompletionHandler[Integer, Unit] {
            def completed(result: Integer, u: Unit): Unit =
              k(ExitResult.Completed(result))

            def failed(t: Throwable, u: Unit): Unit =
              t match {
                case e: Exception => k(ExitResult.Failed(e))
                case _            => k(ExitResult.Terminated(List(t)))
              }
          }
        )

        Async.later[Exception, Int]
      } catch {
        case e: Exception => Async.now(ExitResult.Failed(e))
        case t: Throwable => Async.now(ExitResult.Terminated(List(t)))
      }
    }

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write(b: Buffer[Byte]): IO[Exception, Int] =
    IO.async0[Exception, Int] { k =>
      try {
        val byteBuffer = b.buffer.asInstanceOf[JByteBuffer]
        channel.write(
          byteBuffer,
          (),
          new JCompletionHandler[Integer, Unit] {
            def completed(result: Integer, u: Unit): Unit =
              k(ExitResult.Completed(result))

            def failed(t: Throwable, u: Unit): Unit =
              t match {
                case e: Exception => k(ExitResult.Failed(e))
                case _            => k(ExitResult.Terminated(List(t)))
              }
          }
        )

        Async.later[Exception, Int]
      } catch {
        case e: Exception => Async.now(ExitResult.Failed(e))
        case t: Throwable => Async.now(ExitResult.Terminated(List(t)))
      }
    }

}

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel) {

  // TODO enforce the order of operations? e.g. bind andThen accept(s)?

  /**
   * Binds the channel's socket to a local address and configures the socket to listen for connections.
   */
  def bind(address: InetSocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address)).void

  /**
   * Accepts a connection.
   */
  def accept: IO[Exception, AsynchronousSocketChannel] =
    IO.async0[Exception, AsynchronousSocketChannel] {
      (k: ExitResult[Exception, AsynchronousSocketChannel] => Unit) =>
        try {
          channel
            .accept(
              (),
              new JCompletionHandler[JAsynchronousSocketChannel, Unit] {
                override def completed(result: JAsynchronousSocketChannel, attachment: Unit): Unit =
                  k(ExitResult.Completed(AsynchronousSocketChannel(result)))

                override def failed(t: Throwable, attachment: Unit): Unit =
                  t match {
                    case e: Exception => k(ExitResult.Failed(e))
                    case _            => k(ExitResult.Terminated(List(t)))
                  }
              }
            )

          Async.later[Exception, AsynchronousSocketChannel]
        } catch {
          case e: Exception => Async.now(ExitResult.Failed(e))
          case t: Throwable => Async.now(ExitResult.Terminated(List(t)))
        }
    }

}

object AsynchronousServerSocketChannel {

  // throws IOException
  def apply(): IO[Exception, AsynchronousServerSocketChannel] =
    IO.syncException(JAsynchronousServerSocketChannel.open())
      .map(new AsynchronousServerSocketChannel(_))

  // throws ShutdownChannelGroupException or IOException
  def apply(
    channelGroup: AsynchronousChannelGroup
  ): IO[Exception, AsynchronousServerSocketChannel] =
    IO.syncException(JAsynchronousServerSocketChannel.open(channelGroup.jChannelGroup))
      .map(new AsynchronousServerSocketChannel(_))
}

class AsynchronousChannelGroup(val jChannelGroup: JAsynchronousChannelGroup) {}

object AsynchronousChannelGroup {

  def apply(): IO[Exception, AsynchronousChannelGroup] =
    ??? // IO.syncException { throw new Exception() }
}

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  def connect(socketAddress: InetSocketAddress): IO[Exception, Unit] =
    IO.async0[Exception, Unit] { k =>
      try {
        channel
          .connect(
            socketAddress,
            (),
            new JCompletionHandler[Void, Unit] {
              override def completed(result: Void, attachment: Unit): Unit =
                k(ExitResult.Completed(()))

              override def failed(t: Throwable, attachment: Unit): Unit =
                t match {
                  case e: Exception => k(ExitResult.Failed(e))
                  case _            => k(ExitResult.Terminated(List(t)))
                }
            }
          )

        Async.later[Exception, Unit]
      } catch {
        case e: Exception => Async.now(ExitResult.Failed(e))
        case t: Throwable => Async.now(ExitResult.Terminated(List(t)))
      }
    }
}

object AsynchronousSocketChannel {

  // throws IOException
  def apply(): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(JAsynchronousSocketChannel.open())
      .map(new AsynchronousSocketChannel(_))

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)

  // throws ShutdownChannelGroupException or IOException
  def apply(channelGroup: AsynchronousChannelGroup): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(
        JAsynchronousSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousSocketChannel(_))
}
