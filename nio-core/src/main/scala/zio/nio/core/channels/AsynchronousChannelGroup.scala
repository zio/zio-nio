package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ AsynchronousChannelGroup => JAsynchronousChannelGroup }
import java.nio.channels.spi.{ AsynchronousChannelProvider => JAsynchronousChannelProvider }
import java.util.concurrent.{ ThreadFactory => JThreadFactory }
import java.util.concurrent.TimeUnit

import zio.{ IO, UIO }
import zio.duration._

import scala.concurrent.ExecutionContextExecutorService

object AsynchronousChannelGroup {

  def apply(executor: ExecutionContextExecutorService, initialSize: Int): IO[IOException, AsynchronousChannelGroup] =
    IO.effect(
        new AsynchronousChannelGroup(
          JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)
        )
      )
      .refineToOrDie[IOException]

  def apply(
    threadsNo: Int,
    threadsFactory: JThreadFactory
  ): IO[IOException, AsynchronousChannelGroup] =
    IO.effect(
        new AsynchronousChannelGroup(
          JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)
        )
      )
      .refineToOrDie[IOException]

  def apply(executor: ExecutionContextExecutorService): IO[IOException, AsynchronousChannelGroup] =
    IO.effect(
        new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor))
      )
      .refineToOrDie[IOException]
}

final class AsynchronousChannelGroup(val channelGroup: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration): IO[InterruptedException, Boolean] =
    IO.effect(channelGroup.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))
      .refineToOrDie[InterruptedException]

  val isShutdown: UIO[Boolean] = IO.effectTotal(channelGroup.isShutdown)

  val isTerminated: UIO[Boolean] = IO.effectTotal(channelGroup.isTerminated)

  val provider: UIO[JAsynchronousChannelProvider] = IO.effectTotal(channelGroup.provider())

  val shutdown: UIO[Unit] = IO.effectTotal(channelGroup.shutdown())

  val shutdownNow: IO[IOException, Unit] =
    IO.effect(channelGroup.shutdownNow()).refineToOrDie[IOException]
}
