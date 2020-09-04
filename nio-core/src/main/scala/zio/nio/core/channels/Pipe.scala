package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ Pipe => JPipe }

import zio.nio.core.channels
import zio.{ IO, UIO }

final class Pipe private (private val pipe: JPipe) {

  val source: UIO[Pipe.SourceChannel] =
    IO.effectTotal(new channels.Pipe.SourceChannel(pipe.source()))

  val sink: UIO[Pipe.SinkChannel] =
    IO.effectTotal(new Pipe.SinkChannel(pipe.sink()))
}

object Pipe {

  final class SinkChannel(override protected[channels] val channel: JPipe.SinkChannel)
      extends GatheringByteChannel
      with SelectableChannel

  final class SourceChannel(override protected[channels] val channel: JPipe.SourceChannel)
      extends ScatteringByteChannel
      with SelectableChannel

  val open: IO[IOException, Pipe] =
    IO.effect(new Pipe(JPipe.open())).refineToOrDie[IOException]

  def fromJava(javaPipe: JPipe): Pipe = new Pipe(javaPipe)

}
