package scalaz.nio.channels

import java.nio.channels.{ CompletionHandler => JCompletionHandler }
import java.nio.channels.{ AsynchronousByteChannel => JAsynchronousByteChannel }
import scalaz.nio.Buffer
import scalaz.zio.{ Async, ExitResult, IO }

import java.nio.{ ByteBuffer => JByteBuffer }

class AsynchronousByteChannel(private val channel: JAsynchronousByteChannel) {

  /**
   *  Reads some data into the byte buffer, returning the number of bytes
   *  actually read, or -1 if no bytes were read.
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

  final def write(b: Buffer[Byte]): IO[Exception, Int] = ???
}
