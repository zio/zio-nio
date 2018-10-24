package scalaz.nio.channels

import java.nio.channels.{ CompletionHandler => JCompletionHandler }

import scalaz.zio.{ Async, ExitResult, IO }

object IOAsyncUtil {

  def wrap[A, T](op: JCompletionHandler[T, A] => Unit): IO[Exception, T] =
    IO.async0[Exception, T] { k =>
      val handler = new JCompletionHandler[T, A] {
        def completed(result: T, u: A): Unit =
          k(ExitResult.Completed(result))

        def failed(t: Throwable, u: A): Unit =
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
