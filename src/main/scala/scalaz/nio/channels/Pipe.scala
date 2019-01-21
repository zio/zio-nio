package scalaz.nio.channels

import java.nio.channels.spi.{AbstractSelectableChannel => JAbstractSelectableChannel}
import java.nio.channels.{
  GatheringByteChannel => JGatheringByteChannel,
  ScatteringByteChannel => JScatteringByteChannel,
  Pipe => JPipe
}

import scalaz.zio.IO

class Pipe(private val pipe: JPipe) {

  def source(): IO[Nothing, JAbstractSelectableChannel with JScatteringByteChannel] = // TODO: wrapper for AbstractSelectableChannel and ScatteringByteChannel
    IO.sync(pipe.source())

  def sink(): IO[Nothing, JAbstractSelectableChannel with JGatheringByteChannel] = // TODO: wrapper for AbstractSelectableChannel and GatheringByteChannel
    IO.sync(pipe.sink())
}

object Pipe {

  def open(): IO[Exception, Pipe] =
    IO.syncException(JPipe.open()).map(new Pipe(_))

}
