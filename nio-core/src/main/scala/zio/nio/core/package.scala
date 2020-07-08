package zio.nio

import java.io.EOFException

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

  /**
   * Turns `EOFException` failures into a success with no result.
   */
  def eofOption[R, A, E <: Throwable](effect: ZIO[R, E, A]): ZIO[R, E, Option[A]] =
    effect.asSome.catchSome {
      case _: EOFException => ZIO.none
    }

  implicit final class CloseableResourceOps[-R, +E, +A <: IOCloseable[R]](val value: ZIO[R, E, A]) extends AnyVal {

    /**
     * Lifts an effect producing a closeable NIO resource into a `ZManaged` value.
     */
    def toManagedNio: ZManaged[R, E, A] = value.toManaged(_.close.ignore)

    /**
     * Provides bracketing for any effect producing a closeable NIO resource.
     */
    def bracketNio[R1 <: R, E1 >: E, B](use: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
      value.bracket(_.close.ignore, use)

  }

}
