package zio.nio.channels


import zio.{IO, UIO}

import java.io.IOException
import java.nio.channels.spi.{AsynchronousChannelProvider => JAsynchronousChannelProvider}
import java.nio.channels.{AsynchronousChannelGroup => JAsynchronousChannelGroup}
import java.util.concurrent.{ThreadFactory => JThreadFactory, TimeUnit}
import scala.concurrent.ExecutionContextExecutorService
import zio._

object AsynchronousChannelGroup {

  def apply(executor: ExecutionContextExecutorService, initialSize: Int): IO[IOException, AsynchronousChannelGroup] =
    IO.attempt(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)
      )
    ).refineToOrDie[IOException]

  def apply(
    threadsNo: Int,
    threadsFactory: JThreadFactory
  ): IO[IOException, AsynchronousChannelGroup] =
    IO.attempt(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)
      )
    ).refineToOrDie[IOException]

  def apply(executor: ExecutionContextExecutorService): IO[IOException, AsynchronousChannelGroup] =
    IO.attempt(
      new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor))
    ).refineToOrDie[IOException]

}

final class AsynchronousChannelGroup(val channelGroup: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration): IO[InterruptedException, Boolean] =
    IO.attempt(channelGroup.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))
      .refineToOrDie[InterruptedException]

  val isShutdown: UIO[Boolean] = IO.succeed(channelGroup.isShutdown)

  val isTerminated: UIO[Boolean] = IO.succeed(channelGroup.isTerminated)

  val provider: UIO[JAsynchronousChannelProvider] = IO.succeed(channelGroup.provider())

  val shutdown: UIO[Unit] = IO.succeed(channelGroup.shutdown())

  val shutdownNow: IO[IOException, Unit] =
    IO.attempt(channelGroup.shutdownNow()).refineToOrDie[IOException]

}
