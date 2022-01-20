package zio.nio.channels

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException
import java.nio.channels.spi.{AsynchronousChannelProvider => JAsynchronousChannelProvider}
import java.nio.channels.{AsynchronousChannelGroup => JAsynchronousChannelGroup}
import java.util.concurrent.{ThreadFactory => JThreadFactory, TimeUnit}
import scala.concurrent.ExecutionContextExecutorService

object AsynchronousChannelGroup {

  def apply(executor: ExecutionContextExecutorService, initialSize: Int)(implicit
    trace: ZTraceElement
  ): IO[IOException, AsynchronousChannelGroup] =
    IO.attempt(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)
      )
    ).refineToOrDie[IOException]

  def apply(
    threadsNo: Int,
    threadsFactory: JThreadFactory
  )(implicit trace: ZTraceElement): IO[IOException, AsynchronousChannelGroup] =
    IO.attempt(
      new AsynchronousChannelGroup(
        JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)
      )
    ).refineToOrDie[IOException]

  def apply(
    executor: ExecutionContextExecutorService
  )(implicit trace: ZTraceElement): IO[IOException, AsynchronousChannelGroup] =
    IO.attempt(
      new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor))
    ).refineToOrDie[IOException]

}

final class AsynchronousChannelGroup(val channelGroup: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration)(implicit trace: ZTraceElement): IO[InterruptedException, Boolean] =
    IO.attempt(channelGroup.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))
      .refineToOrDie[InterruptedException]

  def isShutdown(implicit trace: ZTraceElement): UIO[Boolean] = IO.succeed(channelGroup.isShutdown)

  def isTerminated(implicit trace: ZTraceElement): UIO[Boolean] = IO.succeed(channelGroup.isTerminated)

  def provider(implicit trace: ZTraceElement): UIO[JAsynchronousChannelProvider] = IO.succeed(channelGroup.provider())

  def shutdown(implicit trace: ZTraceElement): UIO[Unit] = IO.succeed(channelGroup.shutdown())

  def shutdownNow(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(channelGroup.shutdownNow()).refineToOrDie[IOException]

}
