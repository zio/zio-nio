package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ Pipe => JPipe }

import zio.{ IO, UIO, blocking }

final class Pipe private (private val pipe: JPipe) {

  def sourceBlocking: UIO[Pipe.BlockingSourceChannel] =
    IO.effectTotal(new Pipe.BlockingSourceChannel(pipe.source()))

  def sinkBlocking: UIO[Pipe.BlockingSinkChannel] =
    IO.effectTotal(new Pipe.BlockingSinkChannel(pipe.sink()))

  def sourceNonBlocking: UIO[Pipe.NonBlockingSourceChannel] =
    IO.effectTotal(new Pipe.NonBlockingSourceChannel(pipe.source()))

  def sinkNonBlocking: UIO[Pipe.NonBlockingSinkChannel] =
    IO.effectTotal(new Pipe.NonBlockingSinkChannel(pipe.sink()))

}

object Pipe {

  sealed abstract class SinkChannel[R](override protected[channels] val channel: JPipe.SinkChannel)
      extends ModalChannel
      with GatheringByteChannel[R]

  sealed abstract class SourceChannel[R](override protected[channels] val channel: JPipe.SourceChannel)
      extends ModalChannel
      with ScatteringByteChannel[R]

  final class BlockingSinkChannel(c: JPipe.SinkChannel)
      extends SinkChannel[blocking.Blocking](c)
      with WithEnv.Blocking {}

  final class BlockingSourceChannel(c: JPipe.SourceChannel)
      extends SourceChannel[blocking.Blocking](c)
      with WithEnv.Blocking {}

  final class NonBlockingSinkChannel(c: JPipe.SinkChannel) extends SinkChannel[Any](c) with SelectableChannel {}

  final class NonBlockingSourceChannel(c: JPipe.SourceChannel) extends SourceChannel[Any](c) with SelectableChannel {}

  val open: IO[IOException, Pipe] =
    IO.effect(new Pipe(JPipe.open())).refineToOrDie[IOException]

  def fromJava(javaPipe: JPipe): Pipe = new Pipe(javaPipe)

}
