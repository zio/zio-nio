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

  def apply(executor: ExecutionContextExecutorService, initialSize: Int): IO[Exception, AsynchronousChannelGroup] =
    IO.effect(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)
      )
    ).refineToOrDie[Exception]

  def apply(
    threadsNo: Int,
    threadsFactory: JThreadFactory
  ): IO[Exception, AsynchronousChannelGroup] =
    IO.effect(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)
      )
    ).refineToOrDie[Exception]

  def apply(executor: ExecutionContextExecutorService): IO[Exception, AsynchronousChannelGroup] =
    IO.effect(
      new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor))
    ).refineToOrDie[Exception]
}

class AsynchronousChannelGroup(val channelGroup: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration): IO[Exception, Boolean] =
    IO.effect(channelGroup.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))
      .refineToOrDie[Exception]

  val isShutdown: UIO[Boolean] = IO.effectTotal(channelGroup.isShutdown)

  val isTerminated: UIO[Boolean] = IO.effectTotal(channelGroup.isTerminated)

  val provider: UIO[JAsynchronousChannelProvider] = IO.effectTotal(channelGroup.provider())

  val shutdown: UIO[Unit] = IO.effectTotal(channelGroup.shutdown())

  val shutdownNow: IO[IOException, Unit] =
    IO.effect(channelGroup.shutdownNow()).refineToOrDie[IOException]
}
