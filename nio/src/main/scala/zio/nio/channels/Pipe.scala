package zio.nio
package channels

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Scope, Trace, ZIO}

import java.io.IOException
import java.nio.channels.{Pipe => JPipe}

final class Pipe private (private val pipe: JPipe)(implicit trace: Trace) {

  def source(implicit trace: Trace): ZIO[Scope, Nothing, Pipe.SourceChannel] =
    ZIO.succeed(new channels.Pipe.SourceChannel(pipe.source())).toNioScoped

  def sink(implicit trace: Trace): ZIO[Scope, Nothing, Pipe.SinkChannel] =
    ZIO.succeed(new Pipe.SinkChannel(pipe.sink())).toNioScoped

}

object Pipe {

  final class SinkChannel(override protected[channels] val channel: JPipe.SinkChannel) extends SelectableChannel {

    self =>

    override type BlockingOps = GatheringByteOps

    override type NonBlockingOps = GatheringByteOps

    private object Ops extends GatheringByteOps {
      override protected[channels] def channel = self.channel
    }

    override protected def makeBlockingOps: GatheringByteOps = Ops

    override protected def makeNonBlockingOps: GatheringByteOps = Ops

  }

  final class SourceChannel(override protected[channels] val channel: JPipe.SourceChannel) extends SelectableChannel {

    self =>

    override type BlockingOps = ScatteringByteOps

    override type NonBlockingOps = ScatteringByteOps

    private object Ops extends ScatteringByteOps {
      override protected[channels] def channel = self.channel
    }

    override protected def makeBlockingOps: ScatteringByteOps = Ops

    override protected def makeNonBlockingOps: ScatteringByteOps = Ops

  }

  def open(implicit trace: Trace): IO[IOException, Pipe] =
    ZIO.attempt(new Pipe(JPipe.open())).refineToOrDie[IOException]

  def fromJava(javaPipe: JPipe)(implicit trace: Trace): Pipe = new Pipe(javaPipe)

}
