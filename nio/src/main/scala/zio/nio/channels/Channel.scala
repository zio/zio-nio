package zio.nio.channels
import zio.ZIO.blocking
import zio.nio.IOCloseable
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Trace, UIO, ZIO}

import java.io.IOException
import java.nio.channels.{Channel => JChannel}

trait Channel extends IOCloseable {

  protected val channel: JChannel

  /**
   * Closes this channel.
   */
  final def close(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channel.close()).refineToOrDie[IOException]

  /**
   * Tells whether or not this channel is open.
   */
  final def isOpen(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(channel.isOpen)
}

/**
 * All channels that support blocking operation. (All channels that are not asynchronous)
 */
trait BlockingChannel extends Channel {

  /**
   * The blocking operations supported by this channel.
   */
  type BlockingOps

  final protected def nioBlocking[R, E, A](zioEffect: ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R with Any, E, A] =
    blocking(zioEffect).fork.flatMap(_.join).onInterrupt(close.ignore)

  /**
   * Puts this channel in blocking mode (if applicable) and performs a set of blocking operations. Uses the standard ZIO
   * `Blocking` service to run the provided effect on the blocking thread pool. Installs interrupt handling so that if
   * the ZIO fiber is interrupted, this channel will be closed, which will unblock any currently blocked operations.
   *
   * @param f
   *   Given a `BlockingOps` argument appropriate for this channel type, produces an effect value containing blocking
   *   operations.
   */
  def flatMapBlocking[R, E >: IOException, A](f: BlockingOps => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R with Any, E, A]

}
