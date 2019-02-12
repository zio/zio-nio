package scalaz.nio

import java.nio.{ ByteBuffer => JByteBuffer }

import scalaz.zio.{ IO, RTS }
import testz.{ Harness, assert }

object BufferSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    val initialCapacity = 10
    val newLimit        = 3
    section(
      test("capacity") { () =>
        val testProgram: IO[Exception, Boolean] = for {
          bb <- ByteBuffer.allocate(initialCapacity)
          c1 <- bb.capacity
          c2 <- IO.sync {
                 JByteBuffer.allocate(initialCapacity).capacity
               }
        } yield c1 == c2
        assert(unsafeRun(testProgram))
      }, {

        def allocate = ByteBuffer.allocate(initialCapacity)

        namedSection("allocate")(
          test("capacity initialized") { () =>
            val capacity = unsafeRun(allocate.flatMap(b => b.capacity))
            assert(capacity == initialCapacity)
          },
          test("position is 0") { () =>
            val position = unsafeRun(allocate.flatMap(b => b.position))
            assert(position == 0)
          },
          test("limit is capacity") { () =>
            val limit = unsafeRun(allocate.flatMap(b => b.limit))
            assert(limit == initialCapacity)
          }
        )
      },
      namedSection("position") {
        val newPosition = 3

        def position =
          for {
            b <- ByteBuffer.allocate(initialCapacity)
            _ <- b.position(newPosition)
          } yield b

        test("position set") { () =>
          val actual = unsafeRun(position.flatMap(b => b.position))
          assert(actual == newPosition)
        }
      },
      namedSection("limit")(
        test("limit set") { () =>
          val limit = for {
            b        <- ByteBuffer.allocate(initialCapacity)
            _        <- b.limit(newLimit)
            newLimit <- b.limit
          } yield newLimit

          assert(unsafeRun(limit) == newLimit)
        },
        test("position reset") { () =>
          val positionReset = for {
            b        <- ByteBuffer.allocate(initialCapacity)
            _        <- b.position(newLimit + 1)
            _        <- b.limit(newLimit)
            position <- b.position
          } yield position

          assert(unsafeRun(positionReset) == newLimit)
        }
      ),
      test("reset to marked position") { () =>
        val markedPosition = for {
          b           <- ByteBuffer.allocate(initialCapacity)
          _           <- b.position(1)
          _           <- b.mark
          _           <- b.position(2)
          _           <- b.reset
          newPosition <- b.position
        } yield newPosition

        assert(unsafeRun(markedPosition) == 1)
      }, {
        def clear =
          for {
            b <- ByteBuffer.allocate(initialCapacity)
            _ <- b.position(1)
            _ <- b.mark
            _ <- b.clear
          } yield b

        namedSection("clear")(
          test("position is 0") { () =>
            val position = unsafeRun(clear.flatMap(b => b.position))
            assert(position == 0)
          },
          test("limit is capacity") { () =>
            val limit = unsafeRun(clear.flatMap(b => b.limit))
            assert(limit == initialCapacity)
          }
        )
      }, {
        def flip =
          for {
            b <- ByteBuffer.allocate(initialCapacity)
            _ <- b.position(1)
            _ <- b.flip
          } yield b

        namedSection("flip")(
          test("limit is position") { () =>
            val limit = unsafeRun(flip.flatMap(b => b.limit))
            assert(limit == 1)
          },
          test("position is 0") { () =>
            val position = unsafeRun(flip.flatMap(b => b.position))
            assert(position == 0)
          }
        )
      },
      test("rewind sets position to 0") { () =>
        val rewindedPosition = for {
          b           <- ByteBuffer.allocate(initialCapacity)
          _           <- b.position(1)
          _           <- b.rewind
          newPosition <- b.position
        } yield newPosition
        assert(unsafeRun(rewindedPosition) == 0)
      },
      test("heap buffers a backed by an array") { () =>
        val hasArray = for {
          b        <- ByteBuffer.allocate(initialCapacity)
          hasArray <- b.hasArray
        } yield hasArray
        assert(unsafeRun(hasArray))
      }
    )
  }
}
