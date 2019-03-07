package scalaz.nio

import java.io.IOException

package object io {

  val JustIOException: PartialFunction[Throwable, IOException] = {
    case io: IOException => io
  }

}
