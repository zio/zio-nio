package zio

import com.github.ghik.silencer.silent
import zio.ZManaged.ReleaseMap
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
  private[nio] def eofCheck(value: Int)(implicit trace: ZTraceElement): IO[EOFException, Int] =
    if (value < 0) IO.fail(new EOFException("Channel has reached the end of stream")) else IO.succeed(value)

  /**
   * Handle -1 magic number returned by many Java read APIs when end of file is reached.
   *
   * Produces an `EOFException` failure if `value` < 0, otherwise succeeds with `value`.
   */
  private[nio] def eofCheck(value: Long)(implicit trace: ZTraceElement): IO[EOFException, Long] =
    if (value < 0L) IO.fail(new EOFException("Channel has reached the end of stream")) else IO.succeed(value)

  implicit final class EffectOps[-R, +E, +A](private val effect: ZIO[R, E, A]) extends AnyVal {

    /**
     * Explicitly represent end-of-stream in the error channel.
     *
     * This will catch an `EOFException` failure from the effect and translate it to a failure of `None`. Other
     * exception types are wrapped in `Some`.
     */
    @silent("parameter value ev in method .* is never used")
    def eofCheck[E2 >: E](implicit ev: EOFException <:< E2, trace: ZTraceElement): ZIO[R, Option[E2], A] =
      effect.catchAll {
        case _: EOFException => ZIO.fail(None)
        case e               => ZIO.fail(Some(e))
      }

  }

  implicit final private[nio] class IOCloseableManagement[-R, +E, +A <: IOCloseable](
    private val acquire: ZIO[R, E, A]
  ) extends AnyVal {

    def toNioManaged(implicit trace: ZTraceElement): ZManaged[R, E, A] =
      ZManaged.acquireReleaseInterruptibleWith(acquire)(_.close.ignore)

  }

  implicit final class ManagedOps[-R, +E, +A](private val managed: ZManaged[R, E, A]) extends AnyVal {

    /**
     * Use this managed resource in an effect running in a forked fiber. The resource will be released on the forked
     * fiber after the effect exits, whether it succeeds, fails or is interrupted.
     *
     * @param f
     *   The effect to run in a forked fiber. The resource is only valid within this effect.
     */
    def useForked[R2 <: R, E2 >: E, B](
      f: A => ZIO[R2, E2, B]
    )(implicit trace: ZTraceElement): ZIO[R2, E, Fiber[E2, B]] = ReleaseMap.make.flatMap { releaseMap =>
      managed.zio.flatMap { case (finalizer, a: A) =>
        f(a).onExit(finalizer).fork
      }
    }

  }

  implicit final class ZPipelineCompanionOps(private val pipeline: ZPipeline.type) extends AnyVal {

    /**
     * Creates a pipeline from a chunk processing function.
     */
    /**
     * Creates a pipeline from a chunk processing function.
     */
    def fromPush[Env, Err, In, Out](
      push: => ZManaged[Env, Nothing, Option[Chunk[In]] => ZIO[Env, Err, Chunk[Out]]]
    ): ZPipeline[Env, Err, In, Out] =
      new ZPipeline[Env, Err, In, Out] {
        def apply[Env1 <: Env, Err1 >: Err](stream: ZStream[Env1, Err1, In])(implicit
          trace: ZTraceElement
        ): ZStream[Env1, Err1, Out] = {

          def pull(
            push: Option[Chunk[In]] => ZIO[Env, Err, Chunk[Out]]
          ): ZChannel[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any] =
            ZChannel.readWith[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any](
              in =>
                ZChannel
                  .fromZIO(push(Some(in)))
                  .flatMap(out => ZChannel.write(out))
                  .zipRight[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any](pull(push)),
              err => ZChannel.fail(err),
              _ => ZChannel.fromZIO(push(None)).flatMap(out => ZChannel.write(out))
            )

          val channel: ZChannel[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any] =
            ZChannel.unwrapManaged[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any] {
              push.map(pull)
            }

          stream.pipeThroughChannelOrFail(channel)
        }
      }
  }

  implicit final class ZSinkCompanionOps(private val sink: ZSink.type) {

    /**
     * Creates a sink from a chunk processing function.
     */
    def fromPush[R, E, I, L, Z](
      push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, (Either[E, Z], Chunk[L]), Unit]]
    )(implicit trace: ZTraceElement): ZSink[R, E, I, L, Z] = {

      def pull(
        push: Option[Chunk[I]] => ZIO[R, (Either[E, Z], Chunk[L]), Unit]
      ): ZChannel[R, Nothing, Chunk[I], Any, E, Chunk[L], Z] =
        ZChannel.readWith[R, Nothing, Chunk[I], Any, E, Chunk[L], Z](
          in =>
            ZChannel
              .fromZIO(push(Some(in)))
              .foldChannel[R, Nothing, Chunk[I], Any, E, Chunk[L], Z](
                {
                  case (Left(e), leftovers) =>
                    ZChannel.write(leftovers).zipRight[R, Nothing, Chunk[I], Any, E, Chunk[L], Z](ZChannel.fail(e))
                  case (Right(z), leftovers) =>
                    ZChannel
                      .write(leftovers)
                      .zipRight[R, Nothing, Chunk[I], Any, E, Chunk[L], Z](ZChannel.succeedNow(z))
                },
                _ => pull(push)
              ),
          err => ZChannel.fail(err),
          _ =>
            ZChannel
              .fromZIO(push(None))
              .foldChannel[R, Nothing, Chunk[I], Any, E, Chunk[L], Z](
                {
                  case (Left(e), leftovers) =>
                    ZChannel.write(leftovers).zipRight[R, Nothing, Chunk[I], Any, E, Chunk[L], Z](ZChannel.fail(e))
                  case (Right(z), leftovers) =>
                    ZChannel
                      .write(leftovers)
                      .zipRight[R, Nothing, Chunk[I], Any, E, Chunk[L], Z](ZChannel.succeedNow(z))
                },
                _ => ZChannel.fromZIO(ZIO.dieMessage("empty sink"))
              )
        )

      new ZSink(
        ZChannel.unwrapManaged[R, Nothing, Chunk[I], Any, E, Chunk[L], Z] {
          push.map(pull)
        }
      )
    }

  }

}
