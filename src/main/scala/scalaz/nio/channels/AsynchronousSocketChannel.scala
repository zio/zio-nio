package scalaz.nio.channels

import java.net.InetSocketAddress
import java.nio.channels.{
  AsynchronousSocketChannel => JAsynchronousSocketChannel,
  CompletionHandler => JCompletionHandler
}

import scalaz.zio.{ Async, ExitResult, IO }

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  final def connect(socketAddress: InetSocketAddress): IO[Exception, Unit] =
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

  def apply(): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(JAsynchronousSocketChannel.open())
      .map(new AsynchronousSocketChannel(_))

  def apply(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)

  def apply(channelGroup: AsynchronousChannelGroup): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(
        JAsynchronousSocketChannel.open(channelGroup.jChannelGroup)
      )
      .map(new AsynchronousSocketChannel(_))
}
