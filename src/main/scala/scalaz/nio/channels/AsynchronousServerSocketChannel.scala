package scalaz.nio.channels

import java.net.InetSocketAddress
import java.nio.channels.{
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel,
  CompletionHandler => JCompletionHandler
}

import scalaz.zio.{ Async, ExitResult, IO }

class AsynchronousServerSocketChannel(private val channel: JAsynchronousServerSocketChannel) {

  /**
   * Binds the channel's socket to a local address and configures the socket to listen for connections.
   */
  final def bind(address: InetSocketAddress): IO[Exception, Unit] =
    IO.syncException(channel.bind(address)).void

  /**
   * Accepts a connection.
   */
  final def accept: IO[Exception, AsynchronousSocketChannel] =
    IO.async0[Exception, AsynchronousSocketChannel] { k =>
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
