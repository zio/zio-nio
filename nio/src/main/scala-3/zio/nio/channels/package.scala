package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{ZIO, Trace}

import java.io.IOException

package object channels {

  implicit final class BlockingNioOps[-R, BO, C <: BlockingChannel { type BlockingOps <: BO }](
    private val underlying: ZIO[R, IOException, C]
  ) extends AnyVal {
    type F1[R, E, A] = (C, BO) => ZIO[R, E, A]

    def flatMapNioBlocking[R1, E >: IOException, A](
      f: F1[R1, E, A]
    )(implicit trace: Trace): ZIO[R with R1, E, A] = underlying.flatMap(c => c.flatMapBlocking(f(c, _)))

    def flatMapNioBlockingOps[R1, E >: IOException, A](
      f: BO => ZIO[R1, E, A]
    )(implicit trace: Trace): ZIO[R with R1, E, A] = flatMapNioBlocking((_, ops) => f(ops))

  }

  implicit final class NonBlockingNioOps[-R, BO, C <: SelectableChannel { type NonBlockingOps <: BO }](
    private val underlying: ZIO[R, IOException, C]
  ) extends AnyVal {

    def flatMapNioNonBlocking[R1, E >: IOException, A](f: (C, BO) => ZIO[R1, E, A])(implicit
      trace: Trace
    ): ZIO[R with R1, E, A] =
      underlying.flatMap(c => c.flatMapNonBlocking(f(c, _)))

    def flatMapNioNonBlockingOps[R1, E >: IOException, A](f: BO => ZIO[R1, E, A])(implicit
      trace: Trace
    ): ZIO[R with R1, E, A] =
      flatMapNioNonBlocking((_, ops) => f(ops))

  }

}
