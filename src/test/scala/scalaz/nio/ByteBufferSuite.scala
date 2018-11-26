package scalaz.nio

import scalaz.zio.{ IO, RTS }
import testz.{ Harness, assert }

object ByteBufferSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("") { () =>
        val testProgram: IO[Exception, Boolean] = for {
          b <- Buffer.byte(0)
        } yield true

        assert(unsafeRun(testProgram))
      }
    )
  }
}
