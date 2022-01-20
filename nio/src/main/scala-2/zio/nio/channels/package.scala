package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{ZIO, ZManaged, ZTraceElement}

import java.io.IOException

package object channels {

  implicit final class ManagedBlockingNioOps[-R, +C <: BlockingChannel](
    private val underlying: ZManaged[R, IOException, C]
  ) extends AnyVal {

    def useNioBlocking[R1, E >: IOException, A](
      f: (C, C#BlockingOps) => ZIO[R1, E, A]
    )(implicit trace: ZTraceElement): ZIO[R with R1 with Any, E, A] = underlying.use(c => c.useBlocking(f(c, _)))

    def useNioBlockingOps[R1, E >: IOException, A](
      f: C#BlockingOps => ZIO[R1, E, A]
    )(implicit trace: ZTraceElement): ZIO[R with R1 with Any, E, A] = useNioBlocking((_, ops) => f(ops))

  }

  implicit final class ManagedNonBlockingNioOps[-R, +C <: SelectableChannel](
    private val underlying: ZManaged[R, IOException, C]
  ) extends AnyVal {

    def useNioNonBlocking[R1, E >: IOException, A](f: (C, C#NonBlockingOps) => ZIO[R1, E, A])(implicit
      trace: ZTraceElement
    ): ZIO[R with R1, E, A] =
      underlying.use(c => c.useNonBlocking(f(c, _)))

    def useNioNonBlockingOps[R1, E >: IOException, A](f: C#NonBlockingOps => ZIO[R1, E, A])(implicit
      trace: ZTraceElement
    ): ZIO[R with R1, E, A] =
      useNioNonBlocking((_, ops) => f(ops))

  }

}
