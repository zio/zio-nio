package scalaz.nio.channels

import java.nio.channels.{AsynchronousChannelGroup => JAsynchronousChannelGroup}
import java.nio.channels.spi.{AsynchronousChannelProvider => JAsynchronousChannelProvider}
import java.util.concurrent.{ExecutorService => JExecutorService, ThreadFactory => JThreadFactory}
import java.util.concurrent.TimeUnit

import scalaz.zio.IO
import scalaz.zio.duration.Duration

object AsynchronousChannelGroup {
  def apply(executor: JExecutorService, initialSize: Int): IO[Exception, AsynchronousChannelGroup] =
    IO.syncException(new AsynchronousChannelGroup(JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)))

  def apply(threadsNo: Int, threadsFactory: JThreadFactory): IO[Exception, AsynchronousChannelGroup] =
    IO.syncException(new AsynchronousChannelGroup(JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)))

  def apply(executor: JExecutorService): IO[Exception, AsynchronousChannelGroup] =
    IO.syncException(new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor)))
}

class AsynchronousChannelGroup(private val channel: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration): IO[Exception, Boolean] =
    IO.syncException(channel.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))

  def isShutdown(): Boolean = channel.isShutdown

  def isTerminated(): Boolean = channel.isTerminated

  def provider(): JAsynchronousChannelProvider = channel.provider()

  def shutdown(): Unit = channel.shutdown()

  def shutdownNow(): IO[Exception, Unit] = IO.syncException(channel.shutdownNow())
}
