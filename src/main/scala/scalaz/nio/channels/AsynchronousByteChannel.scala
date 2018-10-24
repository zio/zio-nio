package scalaz.nio.channels

import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  CompletionHandler => JCompletionHandler
}

import scalaz.nio.ByteBuffer
import scalaz.zio.{ Async, ExitResult, IO }

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes
   *  read, or -1 if no bytes were read.
   */
  final def read(b: ByteBuffer): IO[Exception, Int] =
    IO.async0[Exception, Int] { k =>
      try {
        val byteBuffer = b.buffer
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
  final def write(b: ByteBuffer): IO[Exception, Int] =
    IO.async0[Exception, Int] { k =>
      try {
        val byteBuffer = b.buffer
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
