package scalaz.nio

import java.nio.{
  Buffer => JBuffer,
  ByteBuffer => JByteBuffer,
  CharBuffer => JCharBuffer,
  DoubleBuffer => JDoubleBuffer,
  FloatBuffer => JFloatBuffer,
  IntBuffer => JIntBuffer,
  LongBuffer => JLongBuffer,
  ShortBuffer => JShortBuffer
}

import org.scalacheck.Prop.forAll
import org.scalacheck.Test.Passed
import org.scalacheck._
import scalaz.zio.{ Chunk, DefaultRuntime, IO }
import testz.{ Harness, assert }

import scala.reflect.ClassTag

object BufferSuite extends DefaultRuntime {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      namedSection("ByteBuffer")(
        commonBufferTests(
          harness,
          Buffer.byte,
          Buffer.byte,
          JByteBuffer.allocate,
          _.toByte
        )
      ),
      namedSection("CharBuffer") {
        commonBufferTests(
          harness,
          Buffer.char,
          Buffer.char,
          JCharBuffer.allocate,
          _.toChar
        )
      },
      namedSection("DoubleBuffer") {
        commonBufferTests(
          harness,
          Buffer.double,
          Buffer.double,
          JDoubleBuffer.allocate,
          _.toDouble
        )
      },
      namedSection("FloatBuffer") {
        commonBufferTests(
          harness,
          Buffer.float,
          Buffer.float,
          JFloatBuffer.allocate,
          _.toFloat
        )
      },
      namedSection("IntBuffer") {
        commonBufferTests(
          harness,
          Buffer.int,
          Buffer.int,
          JIntBuffer.allocate,
          identity
        )
      },
      namedSection("LongBuffer") {
        commonBufferTests(
          harness,
          Buffer.long,
          Buffer.long,
          JLongBuffer.allocate,
          _.toLong
        )
      },
      namedSection("ShortBuffer") {
        commonBufferTests(
          harness,
          Buffer.short,
          Buffer.short,
          JShortBuffer.allocate,
          _.toShort
        )
      }
    )
  }

  private def commonBufferTests[T, A: ClassTag, B <: JBuffer, C <: Buffer[A]](
    harness: Harness[T],
    allocate: Int => IO[Exception, C],
    wrap: Chunk[A] => IO[Exception, C],
    jAllocate: Int => B,
    f: Int => A
  ): T = {

    import harness._

    val initialCapacity = 10
    def initialValues   = Array(1, 2, 3).map(f)
    def zeroValues      = Array(0, 0, 0).map(f)
//    val zeroValue       = f(0)

    section(
      test("apply") { () =>
        val apply = allocate(3).flatMap(_.array)
        assert(unsafeRun(apply).sameElements(zeroValues))
      },
      namedSection("wrap")(
        test("backed by an array") { () =>
          val wrapArray =
            for {
              buffer <- wrap(Chunk.fromArray(initialValues))
              array  <- buffer.array
            } yield array

          assert(unsafeRun(wrapArray).sameElements(initialValues))
        }
      ),
      // namedSection("get")(
      //   test("at current position") { () =>
      //     val get = wrap(Chunk.fromArray(initialValues)).flatMap(_.get)
      //     assert(unsafeRun(get) == 2)
      //   },
      //   test("at index") { () =>
      //     val get = wrap(Chunk.fromArray(initialValues)).flatMap(_.get(1))

      //     assert(unsafeRun(get) == 2)
      //   },
      //   test("should update position") { () =>
      //     val position =
      //       for {
      //         buffer <- wrap(Chunk.fromArray(initialValues))
      //         _      <- buffer.get
      //         pos    <- buffer.position
      //       } yield pos

      //     assert(unsafeRun(position) == 1)
      //   }
      // ),
      // namedSection("put")(
      //   test("at current position") { () =>
      //     val put = for {
      //       buffer <- wrap(Chunk.fromArray(initialValues))
      //       _      <- buffer.put(zeroValue)
      //       array  <- buffer.array
      //     } yield array

      //     assert(unsafeRun(put).sameElements(Array(0, 2, 3).map(f)))
      //   },
      //   test("at index") { () =>
      //     val put = for {
      //       buffer <- wrap(Chunk.fromArray(initialValues))
      //       _      <- buffer.put(1, zeroValue)
      //       array  <- buffer.array
      //     } yield array

      //     assert(unsafeRun(put).sameElements(Array(1, 0, 3).map(f)))
      //   }
      // ),
      test("capacity") { () =>
        val capacity = unsafeRun(allocate(initialCapacity).flatMap(_.capacity))
        assert(capacity == jAllocate(initialCapacity).capacity)
      },
      namedSection("allocate")(
        test("capacity initialized") { () =>
          val capacity = unsafeRun(allocate(initialCapacity).flatMap(_.capacity))
          assert(capacity == initialCapacity)
        },
        test("position is 0") { () =>
          val position = unsafeRun(allocate(initialCapacity).flatMap(_.position))
          assert(position == 0)
        },
        test("limit is capacity") { () =>
          val limit = unsafeRun(allocate(initialCapacity).flatMap(_.limit))
          assert(limit == initialCapacity)
        }
      ),
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
      namedSection("limit") {
        val newLimit = 3

        section(
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
        )
      },
      test("reset to marked position") { () =>
        val markedPosition = for {
          b           <- allocate(initialCapacity)
          _           <- b.position(1)
          _           <- b.mark
          _           <- b.position(2)
          _           <- b.reset
          newPosition <- b.position
        } yield newPosition

        assert(unsafeRun(markedPosition) == 1)
      },
      namedSection("clear") {
        def clear =
          for {
            b <- allocate(initialCapacity)
            _ <- b.position(1)
            _ <- b.mark
            _ <- b.clear
          } yield b

        section(
          test("position is 0") { () =>
            val position = unsafeRun(clear.flatMap(_.position))
            assert(position == 0)
          },
          test("limit is capacity") { () =>
            val limit = unsafeRun(clear.flatMap(_.limit))
            assert(limit == initialCapacity)
          }
        )
      },
      namedSection("flip") {
        def flip =
          for {
            b <- allocate(initialCapacity)
            _ <- b.position(1)
            _ <- b.flip
          } yield b

        section(
          test("limit is position") { () =>
            val limit = unsafeRun(flip.flatMap(_.limit))
            assert(limit == 1)
          },
          test("position is 0") { () =>
            val position = unsafeRun(flip.flatMap(_.position))
            assert(position == 0)
          }
        )
      },
      test("rewind sets position to 0") { () =>
        val rewindedPosition =
          for {
            b           <- allocate(initialCapacity)
            _           <- b.position(1)
            _           <- b.rewind
            newPosition <- b.position
          } yield newPosition
        assert(unsafeRun(rewindedPosition) == 0)
      },
      test("heap buffers a backed by an array") { () =>
        val hasArray =
          for {
            b        <- allocate(initialCapacity)
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
                    case _: IllegalArgumentException | _: IllegalStateException =>
                      IO.effectTotal(true)
                  })
              }

              assert(Test.check(Test.Parameters.default, prop).status == Passed)
          }
        )
      }
    )
  }
}
