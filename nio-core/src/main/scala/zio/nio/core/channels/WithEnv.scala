package zio.nio.core.channels

import zio.{ IO, ZIO, blocking }

private[channels] trait WithEnv[R] {

  protected def withEnv[E, A](effect: IO[E, A]): ZIO[R, E, A]

}

private[channels] object WithEnv {

  trait Blocking extends WithEnv[blocking.Blocking] {
    override protected def withEnv[E, A](effect: IO[E, A]): ZIO[blocking.Blocking, E, A] = blocking.blocking(effect)
  }

  trait NonBlocking extends WithEnv[Any] {
    override protected def withEnv[E, A](effect: IO[E, A]): ZIO[Any, E, A] = effect
  }

}
