package scalaz.nio.channels

import java.io.IOException
import java.nio.channels.{ Pipe => JPipe }

import scalaz.nio.io._
import scalaz.zio.IO

class Pipe(private val pipe: JPipe) {

  final val source: IO[Nothing, ScatteringByteChannel] =
    IO.sync(new ScatteringByteChannel(pipe.source()))

  final val sink: IO[Nothing, GatheringByteChannel] =
    IO.sync(new GatheringByteChannel(pipe.sink()))
}

object Pipe {

  final val open: IO[IOException, Pipe] =
    IO.syncCatch(new Pipe(JPipe.open()))(JustIOException)

}
