package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Trace, ZIO}

import java.io.IOException

package object channels {

  implicit final class BlockingNioOps[-R, +C <: BlockingChannel](
    private val underlying: ZIO[R, IOException, C]
  ) extends AnyVal {

    def flatMapNioBlocking[R1, E >: IOException, A](
      f: (C, C#BlockingOps) => ZIO[R1, E, A]
    )(implicit trace: Trace): ZIO[R with R1 with Any, E, A] =
      underlying.flatMap(c => c.flatMapBlocking(f(c, _)))

    def flatMapNioBlockingOps[R1, E >: IOException, A](
      f: C#BlockingOps => ZIO[R1, E, A]
    )(implicit trace: Trace): ZIO[R with R1 with Any, E, A] = flatMapNioBlocking((_, ops) => f(ops))

  }

  implicit final class NonBlockingNioOps[-R, +C <: SelectableChannel](
    private val underlying: ZIO[R, IOException, C]
  ) extends AnyVal {

    def flatMapNioNonBlocking[R1, E >: IOException, A](f: (C, C#NonBlockingOps) => ZIO[R1, E, A])(implicit
      trace: Trace
    ): ZIO[R with R1, E, A] =
      underlying.flatMap(c => c.flatMapNonBlocking(f(c, _)))

    def flatMapNioNonBlockingOps[R1, E >: IOException, A](f: C#NonBlockingOps => ZIO[R1, E, A])(implicit
      trace: Trace
    ): ZIO[R with R1, E, A] =
      flatMapNioNonBlocking((_, ops) => f(ops))

  }

}
