package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{ZIO, ZManaged, ZTraceElement}

import java.io.IOException

package object channels {

  implicit final class ManagedBlockingNioOps[-R, BO, C <: BlockingChannel { type BlockingOps <: BO }](
    private val underlying: ZManaged[R, IOException, C]
  ) extends AnyVal {
    type F1[R, E, A] = (C, BO) => ZIO[R, E, A]

    def useNioBlocking[R1, E >: IOException, A](
      f: F1[R1, E, A]
    )(implicit trace: ZTraceElement): ZIO[R with R1, E, A] = underlying.use(c => c.useBlocking(f(c, _)))

    def useNioBlockingOps[R1, E >: IOException, A](
      f: BO => ZIO[R1, E, A]
    )(implicit trace: ZTraceElement): ZIO[R with R1, E, A] = useNioBlocking((_, ops) => f(ops))

  }

  implicit final class ManagedNonBlockingNioOps[-R, BO, C <: SelectableChannel { type NonBlockingOps <: BO }](
    private val underlying: ZManaged[R, IOException, C]
  ) extends AnyVal {

    def useNioNonBlocking[R1, E >: IOException, A](f: (C, BO) => ZIO[R1, E, A])(implicit
      trace: ZTraceElement
    ): ZIO[R with R1, E, A] =
      underlying.use(c => c.useNonBlocking(f(c, _)))

    def useNioNonBlockingOps[R1, E >: IOException, A](f: BO => ZIO[R1, E, A])(implicit
      trace: ZTraceElement
    ): ZIO[R with R1, E, A] =
      useNioNonBlocking((_, ops) => f(ops))

  }

}
