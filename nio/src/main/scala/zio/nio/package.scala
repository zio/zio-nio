package zio

import com.github.ghik.silencer.silent
import zio.ZManaged.ReleaseMap

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
  private[nio] def eofCheck(value: Int): IO[EOFException, Int] =
    if (value < 0) IO.fail(new EOFException("Channel has reached the end of stream")) else IO.succeed(value)

  /**
   * Handle -1 magic number returned by many Java read APIs when end of file is reached.
   *
   * Produces an `EOFException` failure if `value` < 0, otherwise succeeds with `value`.
   */
  private[nio] def eofCheck(value: Long): IO[EOFException, Long] =
    if (value < 0L) IO.fail(new EOFException("Channel has reached the end of stream")) else IO.succeed(value)

  implicit final class EffectOps[-R, +E, +A](private val effect: ZIO[R, E, A]) extends AnyVal {

    /**
     * Explicitly represent end-of-stream in the error channel.
     *
     * This will catch an `EOFException` failure from the effect and translate it to a failure of `None`. Other
     * exception types are wrapped in `Some`.
     */
    @silent("parameter value ev in method .* is never used")
    def eofCheck[E2 >: E](implicit ev: EOFException <:< E2): ZIO[R, Option[E2], A] =
      effect.catchAll {
        case _: EOFException => ZIO.fail(None)
        case e               => ZIO.fail(Some(e))
      }

  }

  implicit final private[nio] class IOCloseableManagement[-R, +E, +A <: IOCloseable](
    private val acquire: ZIO[R, E, A]
  ) extends AnyVal {

    def toNioManaged: ZManaged[R, E, A] = ZManaged.makeInterruptible(acquire)(_.close.ignore)

  }

  implicit final class ManagedOps[-R, +E, +A](private val managed: ZManaged[R, E, A]) extends AnyVal {

    /**
     * Use this managed resource in an effect running in a forked fiber. The resource will be released on the forked
     * fiber after the effect exits, whether it succeeds, fails or is interrupted.
     *
     * @param f
     *   The effect to run in a forked fiber. The resource is only valid within this effect.
     */
    def useForked[R2 <: R, E2 >: E, B](f: A => ZIO[R2, E2, B]): ZIO[R2, E, Fiber[E2, B]] =
      ReleaseMap.make.flatMap { releaseMap =>
        managed.zio.provideSome[R]((_, releaseMap)).flatMap { case (finalizer, a) => f(a).onExit(finalizer).fork }
      }

  }

}
