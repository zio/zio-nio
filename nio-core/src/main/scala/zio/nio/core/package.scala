package zio.nio

import java.io.EOFException

import com.github.ghik.silencer.silent
import zio.{ IO, ZIO, ZManaged }

package object core {

  /**
   * Handle -1 magic number returned by many Java read APIs when end of file is reached.
   *
   * Produces an `EOFException` failure if `value` < 0, otherwise succeeds with `value`.
   */
  private[nio] def eofCheck(value: Int): IO[EOFException, Int] =
    if (value < 0) IO.fail(new EOFException("Channel has reached the end of stream")) else IO.succeed(value)

  /**
   * Handle -1 magic number returned by many Java read APIs when end of file is reached.
   *
   * Produces an `EOFException` failure if `value` < 0, otherwise succeeds with `value`.
   */
  private[nio] def eofCheck(value: Long): IO[EOFException, Long] =
    if (value < 0L) IO.fail(new EOFException("Channel has reached the end of stream")) else IO.succeed(value)

  implicit final class EffectOps[-R, +E, +A](val effect: ZIO[R, E, A]) extends AnyVal {

    /**
     * Explicitly represent end-of-stream in the error channel.
     *
     * This will catch an `EOFException` failure from the effect and translate it to a failure of `None`.
     * Other exception types are wrapped in `Some`.
     */
    @silent("parameter value ev in method .* is never used")
    def eofCheck[E2 >: E](implicit ev: EOFException <:< E2): ZIO[R, Option[E2], A] =
      effect.catchAll {
        case _: EOFException => ZIO.fail(None)
        case e               => ZIO.fail(Some(e))
      }
  }

  implicit final private[nio] class IOCloseableManagement[-R, +E, +A <: IOCloseable](
    val acquire: ZIO[R, E, A]
  ) extends AnyVal {

    def toNioManaged: ZManaged[R, E, A] = ZManaged.makeInterruptible(acquire)(_.close.ignore)

  }
}
