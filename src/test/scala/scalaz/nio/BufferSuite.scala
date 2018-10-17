package scalaz.nio

import java.nio.{ ByteBuffer => JByteBuffer }

import scalaz.zio.{ IO, RTS }
import testz.{ Harness, assert }

object BufferSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    val initialCapacity = 10
    section(
      test("capacity") { () =>
        val testProgram: IO[Exception, Boolean] = for {
          bb <- Buffer.byte(initialCapacity)
          c1 <- bb.capacity
          c2 <- IO.sync {
                 JByteBuffer.allocate(initialCapacity).capacity
               }
        } yield c1 != c2
        assert(unsafeRun(testProgram))
      }
    )

    namedSection("allocate") {

      def allocate = Buffer.byte(initialCapacity)

      test("capacity initialized") { () =>
        val capacity = unsafeRun(allocate.flatMap(b => b.capacity))
        assert(capacity == initialCapacity)
      }

      test("position is 0") { () =>
        val position = unsafeRun(allocate.flatMap(b => b.position))
        assert(position == 0)
      }

      test("limit is capacity") { () =>
        val limit = unsafeRun(allocate.flatMap(b => b.limit))
        assert(limit == initialCapacity)
      }
    }

    namedSection("position") {
      val newPosition = 3

      def position =
        for {
          b <- Buffer.byte(initialCapacity)
          _ <- b.position(newPosition)
        } yield b

      test("position set") { () =>
        val actual = unsafeRun(position.flatMap(b => b.position))
        assert(actual == newPosition)
      }
    }

    namedSection("limit") {
      val newLimit = 3

      test("limit set") { () =>
        val limit = for {
          b        <- Buffer.byte(initialCapacity)
          _        <- b.limit(newLimit)
          newLimit <- b.limit
        } yield newLimit

        assert(unsafeRun(limit) == newLimit)
      }

      test("position reset") { () =>
        val positionReset = for {
          b        <- Buffer.byte(initialCapacity)
          _        <- b.position(newLimit + 1)
          _        <- b.limit(newLimit)
          position <- b.position
        } yield position

        assert(unsafeRun(positionReset) == newLimit)
      }
    }
  }

}
