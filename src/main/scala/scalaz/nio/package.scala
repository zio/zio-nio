package scalaz.nio

import java.io.IOException

import scalaz.zio.IO

package object io {

  implicit class IOObjOps(private val ioObj: IO.type) extends AnyVal {

    def syncIOException[A](effect: => A): IO[IOException, A] =
      IO.syncCatch(effect) {
        case e: IOException => e
      }
  }

}
