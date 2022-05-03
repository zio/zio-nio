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
    trace: Trace
  ): IO[IOException, AsynchronousChannelGroup] =
    ZIO
      .attempt(
        new AsynchronousChannelGroup(
          JAsynchronousChannelGroup.withCachedThreadPool(executor, initialSize)
        )
      )
      .refineToOrDie[IOException]

  def apply(
    threadsNo: Int,
    threadsFactory: JThreadFactory
  )(implicit trace: Trace): IO[IOException, AsynchronousChannelGroup] =
    ZIO
      .attempt(
        new AsynchronousChannelGroup(
          JAsynchronousChannelGroup.withFixedThreadPool(threadsNo, threadsFactory)
        )
      )
      .refineToOrDie[IOException]

  def apply(
    executor: ExecutionContextExecutorService
  )(implicit trace: Trace): IO[IOException, AsynchronousChannelGroup] =
    ZIO
      .attempt(
        new AsynchronousChannelGroup(JAsynchronousChannelGroup.withThreadPool(executor))
      )
      .refineToOrDie[IOException]

}

final class AsynchronousChannelGroup(val channelGroup: JAsynchronousChannelGroup) {

  def awaitTermination(timeout: Duration)(implicit trace: Trace): IO[InterruptedException, Boolean] =
    ZIO
      .attempt(channelGroup.awaitTermination(timeout.asJava.toMillis, TimeUnit.MILLISECONDS))
      .refineToOrDie[InterruptedException]

  def isShutdown(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(channelGroup.isShutdown)

  def isTerminated(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(channelGroup.isTerminated)

  def provider(implicit trace: Trace): UIO[JAsynchronousChannelProvider] = ZIO.succeed(channelGroup.provider())

  def shutdown(implicit trace: Trace): UIO[Unit] = ZIO.succeed(channelGroup.shutdown())

  def shutdownNow(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(channelGroup.shutdownNow()).refineToOrDie[IOException]

}
