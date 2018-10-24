package scalaz.nio.channels

import java.nio.channels.{ CompletionHandler => JCompletionHandler }

import scalaz.zio.{ Async, ExitResult, IO }

object IOAsyncUtil {

  def wrap[T](op: JCompletionHandler[T, Unit] => Unit): IO[Exception, T] =
    IO.async0[Exception, T] { k =>
      val handler = new JCompletionHandler[T, Unit] {
        def completed(result: T, u: Unit): Unit =
          k(ExitResult.Completed(result))

        def failed(t: Throwable, u: Unit): Unit =
          t match {
            case e: Exception => k(ExitResult.Failed(e))
            case _            => k(ExitResult.Terminated(List(t)))
          }
      }

      try {
        op(handler)
        Async.later[Exception, T]
      } catch {
        case e: Exception => Async.now(ExitResult.Failed(e))
        case t: Throwable => Async.now(ExitResult.Terminated(List(t)))
      }
    }
}
