package scalaz.nio.channels

import java.io.IOException
import java.nio.channels.{ AsynchronousChannelGroup => JAsynchronousChannelGroup }
import java.nio.channels.spi.{ AsynchronousChannelProvider => JAsynchronousChannelProvider }

import java.util.concurrent.{ ExecutorService => JExecutorService, ThreadFactory => JThreadFactory }
import java.util.concurrent.TimeUnit

import scalaz.nio.io._
import scalaz.zio.{ IO, JustExceptions, UIO }
import scalaz.zio.duration.Duration

object AsynchronousChannelGroup {

  def apply(executor: JExecutorService, initialSize: Int): IO[Exception, AsynchronousChannelGroup] =
    IO.effect(
        new AsynchronousChannelGroup(
          JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)
        )
      )
      .refineOrDie(JustExceptions)

  def apply(
    threadsNo: Int,
    threadsFactory: JThreadFactory
  ): IO[Exception, AsynchronousChannelGroup] =
    IO.effect(
        new AsynchronousChannelGroup(
          JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)
        )
      )
      .refineOrDie(JustExceptions)

  def apply(executor: JExecutorService): IO[Exception, AsynchronousChannelGroup] =
    IO.effect(
        new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor))
      )
      .refineOrDie(JustExceptions)
}

class AsynchronousChannelGroup(private[channels] val channelGroup: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration): IO[Exception, Boolean] =
    IO.effect(channelGroup.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))
      .refineOrDie(JustExceptions)

  def isShutdown: UIO[Boolean] = IO.effectTotal(channelGroup.isShutdown)

  def isTerminated: UIO[Boolean] = IO.effectTotal(channelGroup.isTerminated)

  def provider: UIO[JAsynchronousChannelProvider] = IO.effectTotal(channelGroup.provider())

  def shutdown(): UIO[Unit] = IO.effectTotal(channelGroup.shutdown())

  def shutdownNow(): IO[IOException, Unit] =
    IO.effect(channelGroup.shutdownNow()).refineOrDie(JustIOException)
}
