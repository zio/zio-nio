package zio.nio
package channels

import zio.{IO, Managed}

import java.io.IOException
import java.nio.channels.{Pipe => JPipe}

final class Pipe private (private val pipe: JPipe) {

  val source: Managed[Nothing, Pipe.SourceChannel] =
    IO.effectTotal(new channels.Pipe.SourceChannel(pipe.source())).toNioManaged

  val sink: Managed[Nothing, Pipe.SinkChannel] =
    IO.effectTotal(new Pipe.SinkChannel(pipe.sink())).toNioManaged

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

  val open: IO[IOException, Pipe] =
    IO.effect(new Pipe(JPipe.open())).refineToOrDie[IOException]

  def fromJava(javaPipe: JPipe): Pipe = new Pipe(javaPipe)

}
