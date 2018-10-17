package scalaz.nio

import scalaz.zio.IO
import scalaz.zio.RTS
import testz. { Harness, assert }
import java.nio. { ByteBuffer => JByteBuffer }

object BufferSuite extends RTS {
  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("capacity") { () =>
        val testProgram: IO[Exception, Boolean] = for {
          bb  <- Buffer.byte(10)
          c1  <- bb.capacity
          c2  <- IO.sync {
            JByteBuffer.allocate(10).capacity
          }
        } yield (c1 == 0)
        assert(unsafeRun(testProgram))
      }
    )
  }
}
