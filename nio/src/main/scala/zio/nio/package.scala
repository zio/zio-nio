package zio

import com.github.ghik.silencer.silent
import zio.stream.{ZChannel, ZPipeline, ZSink, ZStream}

import java.io.EOFException

/**
 * ZIO-NIO, the API for using Java's NIO API in ZIO programs.
 */
package object nio {

  /**
   * Handle -1 magic number returned by many Java read APIs when end of file is reached.
   *
   * Produces an `EOFException` failure if `value` < 0, otherwise succeeds with `value`.
   */
  private[nio] def eofCheck(value: Int)(implicit trace: Trace): IO[EOFException, Int] =
    if (value < 0) ZIO.fail(new EOFException("Channel has reached the end of stream")) else ZIO.succeed(value)

  /**
   * Handle -1 magic number returned by many Java read APIs when end of file is reached.
   *
   * Produces an `EOFException` failure if `value` < 0, otherwise succeeds with `value`.
   */
  private[nio] def eofCheck(value: Long)(implicit trace: Trace): IO[EOFException, Long] =
    if (value < 0L) ZIO.fail(new EOFException("Channel has reached the end of stream")) else ZIO.succeed(value)

  implicit final class EffectOps[-R, +E, +A](private val effect: ZIO[R, E, A]) extends AnyVal {

    /**
     * Explicitly represent end-of-stream in the error channel.
     *
     * This will catch an `EOFException` failure from the effect and translate it to a failure of `None`. Other
     * exception types are wrapped in `Some`.
     */
    @silent("parameter value ev in method .* is never used")
    def eofCheck[E2 >: E](implicit ev: EOFException <:< E2, trace: Trace): ZIO[R, Option[E2], A] =
      effect.catchAll {
        case _: EOFException => ZIO.fail(None)
        case e               => ZIO.fail(Some(e))
      }

  }

  implicit final private[nio] class IOCloseableManagement[-R, +E, +A <: IOCloseable](
    private val acquire: ZIO[R, E, A]
  ) extends AnyVal {

    def toNioScoped(implicit trace: Trace): ZIO[R with Scope, E, A] =
      acquire.tap(a => ZIO.addFinalizer(a.close.ignore))

  }
}
