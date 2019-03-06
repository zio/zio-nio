package scalaz.nio.channels

import java.io.IOException
import java.nio.channels.{ Pipe => JPipe }

import scalaz.nio.io._
import scalaz.zio.{ IO, UIO }

class Pipe(private val pipe: JPipe) {

  final val source: UIO[ScatteringByteChannel] =
    IO.effectTotal(new ScatteringByteChannel(pipe.source()))

  final val sink: UIO[GatheringByteChannel] =
    IO.effectTotal(new GatheringByteChannel(pipe.sink()))
}

object Pipe {

  final val open: IO[IOException, Pipe] =
    IO.effect(new Pipe(JPipe.open())).refineOrDie(JustIOException)

}
