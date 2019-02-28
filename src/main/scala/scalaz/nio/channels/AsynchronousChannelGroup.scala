package scalaz.nio.channels

import java.io.IOException
import java.nio.channels.{ AsynchronousChannelGroup => JAsynchronousChannelGroup }
import java.nio.channels.spi.{ AsynchronousChannelProvider => JAsynchronousChannelProvider }

import java.util.concurrent.{ ExecutorService => JExecutorService, ThreadFactory => JThreadFactory }
import java.util.concurrent.TimeUnit

import scalaz.zio.IO
import scalaz.zio.duration.Duration

object AsynchronousChannelGroup {

  def apply(executor: JExecutorService, initialSize: Int): IO[Exception, AsynchronousChannelGroup] =
    IO.syncException(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)
      )
    )

  def apply(
    threadsNo: Int,
    threadsFactory: JThreadFactory
  ): IO[Exception, AsynchronousChannelGroup] =
    IO.syncException(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)
      )
    )

  def apply(executor: JExecutorService): IO[Exception, AsynchronousChannelGroup] =
    IO.syncException(
      new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor))
    )
}

class AsynchronousChannelGroup(private[channels] val channelGroup: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration): IO[Exception, Boolean] =
    IO.syncException(channelGroup.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))

  def isShutdown: IO[Nothing, Boolean] = IO.sync(channelGroup.isShutdown)

  def isTerminated: IO[Nothing, Boolean] = IO.sync(channelGroup.isTerminated)

  def provider: IO[Nothing, JAsynchronousChannelProvider] = IO.sync(channelGroup.provider())

  def shutdown(): IO[Nothing, Unit] = IO.sync(channelGroup.shutdown())

  def shutdownNow(): IO[IOException, Unit] = IO.syncCatch(channelGroup.shutdownNow()) {
    case e: IOException => e
  }
}
