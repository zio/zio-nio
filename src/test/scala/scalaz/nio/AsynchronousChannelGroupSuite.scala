package scalaz.nio
import scalaz.zio.RTS

import testz.{Harness, assert}

object AsynchronousChannelGroupSuite extends RTS{
  def tests[T](harness: Harness[T]): T = {
    import harness._

  }
}
