package scalaz.nio.channels

import java.nio.channels.{ CompletionHandler => JCompletionHandler }
import java.nio.channels.{ AsynchronousByteChannel => JAsynchronousByteChannel }
import java.nio.channels.{ AsynchronousSocketChannel => JAsynchronousSocketChannel }
import java.nio.channels.{ AsynchronousChannelGroup => JAsynchronousChannelGroup }
import scalaz.nio.Buffer
import scalaz.zio.{ Async, ExitResult, IO }

import java.nio.{ ByteBuffer => JByteBuffer }

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final def read(b: Buffer[Byte]): IO[Exception, Int] =
    IO.async0[Exception, Int] { (k: ExitResult[Exception, Int] => Unit) =>
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
    IO.async0[Exception, Int] { (k: ExitResult[Exception, Int] => Unit) =>
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

class AsynchronousChannelGroup(val jChannelGroup: JAsynchronousChannelGroup) {}

object AsynchronousChannelGroup {

  def apply(): IO[Exception, AsynchronousChannelGroup] =
    ??? // IO.syncException { throw new Exception() }
}

class AsynchronousSocketChannel(private val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {}

object AsynchronousSocketChannel {

  // throws IOException
  def apply(): IO[Exception, AsynchronousSocketChannel] =
    IO.syncException(JAsynchronousSocketChannel.open()).map(new AsynchronousSocketChannel(_))

  // throws ShutdownChannelGroupException or IOException
  def apply(channelGroup: AsynchronousChannelGroup) =
    IO.syncException(JAsynchronousSocketChannel.open(channelGroup.jChannelGroup))
      .map(new AsynchronousSocketChannel(_))
}

/**
 * Only use casses.
 */
object Program {
  val src  = Buffer.byte(0)
  val sink = Buffer.byte(0)

  val program1: IO[Exception, (Int, Int)] = AsynchronousSocketChannel().flatMap { socketChannel =>
    socketChannel.write(src).flatMap { nSrc =>
      socketChannel.read(sink).map(nSink => (nSrc, nSink))
    }
  }

  val program2: IO[Exception, (Int, Int)] = for {
    channelGroup <- AsynchronousChannelGroup()
    channel      <- AsynchronousSocketChannel(channelGroup)
    nSrc         <- channel.write(src)
    nSink        <- channel.read(sink)
  } yield (nSrc, nSink)
}
