package scalaz.nio

import java.nio.{ ByteBuffer => JByteBuffer }

import org.scalacheck.Prop.forAll
import org.scalacheck.Test.Passed
import org.scalacheck._
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
          bb <- Buffer.byte(initialCapacity)
          c1 <- bb.capacity
          c2 <- IO.sync {
                 JByteBuffer.allocate(initialCapacity).capacity
               }
        } yield c1 == c2
        assert(unsafeRun(testProgram))
      }, {

        def allocate = Buffer.byte(initialCapacity)

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
        test("position set") { () =>
          val position = unsafeRun(
            Buffer
              .byte(initialCapacity)
              .flatMap { b =>
                val readPosition: IO[Nothing, Int] = b.position
                for {
                  _  <- b.position(3)
                  p1 <- readPosition
                } yield p1
              }
          )
          assert(position == 3)
        }
      },
      namedSection("limit")(
        test("limit set") { () =>
          val limit = Buffer
            .byte(initialCapacity)
            .flatMap { b =>
              val readLimit: IO[Nothing, Int] = b.limit
              for {
                _        <- b.limit(newLimit)
                newLimit <- readLimit
              } yield newLimit
            }
          assert(unsafeRun(limit) == newLimit)
        },
        test("position reset") { () =>
          val positionReset =
            Buffer.byte(initialCapacity).flatMap { b =>
              val readPosition: IO[Nothing, Int] = b.position
              for {
                _        <- b.position(newLimit + 1)
                _        <- b.limit(newLimit)
                position <- readPosition
              } yield position
            }
          assert(unsafeRun(positionReset) == newLimit)
        }
      ),
      test("reset to marked position") { () =>
        val markedPosition = for {
          b           <- Buffer.byte(initialCapacity)
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
            b <- Buffer.byte(initialCapacity)
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
            b <- Buffer.byte(initialCapacity)
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
          b           <- Buffer.byte(initialCapacity)
          _           <- b.position(1)
          _           <- b.rewind
          newPosition <- b.position
        } yield newPosition
        assert(unsafeRun(rewindedPosition) == 0)
      },
      test("heap buffers a backed by an array") { () =>
        val hasArray = for {
          b        <- Buffer.byte(initialCapacity)
          hasArray <- b.hasArray
        } yield hasArray
        assert(unsafeRun(hasArray))
      }, {
        namedSection("invariant")(
          test("0 <= mark <= position <= limit <= capacity") {
            () =>
              implicit val arbitraryInt: Arbitrary[Int] = Arbitrary {
                Gen.choose(-1, 10)
              }

              val prop = forAll {
                (markedPosition: Int, position: Int, limit: Int, capacity: Int) =>
                  val isInvariantPreserved = for {
                    b    <- Buffer.byte(capacity)
                    _    <- b.limit(limit)
                    _    <- b.position(markedPosition)
                    _    <- b.mark
                    _    <- b.position(position)
                    _    <- b.reset
                    mark <- b.position
                  } yield 0 <= mark && mark <= position && position <= limit && limit <= capacity

                  // either invariant holds or exception was caught
                  unsafeRun(isInvariantPreserved.catchSome {
                    case _: IllegalArgumentException | _: IllegalStateException => IO.sync(true)
                  })
              }

              assert(Test.check(Test.Parameters.default, prop).status == Passed)
          }
        )
      }
    )
  }

}
