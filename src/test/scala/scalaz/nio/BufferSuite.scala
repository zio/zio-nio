package scalaz.nio

import java.nio.{
//  ByteOrder,
  Buffer => JBuffer,
  ByteBuffer => JByteBuffer,
  CharBuffer => JCharBuffer,
  DoubleBuffer => JDoubleBuffer,
  FloatBuffer => JFloatBuffer,
  IntBuffer => JIntBuffer,
  LongBuffer => JLongBuffer,
  ShortBuffer => JShortBuffer
}

import scalaz.zio.{ IO, RTS }
import testz.{ Harness, assert }

import scala.reflect.ClassTag

object BufferSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      namedSection("ByteBuffer")(
        commonBufferTests(
          harness,
          ByteBuffer.allocate,
          ByteBuffer.wrap,
          ByteBuffer.wrap,
          JByteBuffer.allocate,
          _.toByte
        )
      ),
      namedSection("CharBuffer") {
        commonBufferTests(
          harness,
          CharBuffer.allocate,
          CharBuffer.wrap,
          CharBuffer.wrap,
          JCharBuffer.allocate,
          _.toChar
        )
      },
      namedSection("DoubleBuffer") {
        commonBufferTests(
          harness,
          DoubleBuffer.allocate,
          DoubleBuffer.wrap,
          DoubleBuffer.wrap,
          JDoubleBuffer.allocate,
          _.toDouble
        )
      },
      namedSection("FloatBuffer") {
        commonBufferTests(
          harness,
          FloatBuffer.allocate,
          FloatBuffer.wrap,
          FloatBuffer.wrap,
          JFloatBuffer.allocate,
          _.toFloat
        )
      },
      namedSection("IntBuffer") {
        commonBufferTests(
          harness,
          IntBuffer.allocate,
          IntBuffer.wrap,
          IntBuffer.wrap,
          JIntBuffer.allocate,
          identity
        )
      },
      namedSection("LongBuffer") {
        commonBufferTests(
          harness,
          LongBuffer.allocate,
          LongBuffer.wrap,
          LongBuffer.wrap,
          JLongBuffer.allocate,
          _.toLong
        )
      },
      namedSection("ShortBuffer") {
        commonBufferTests(
          harness,
          ShortBuffer.allocate,
          ShortBuffer.wrap,
          ShortBuffer.wrap,
          JShortBuffer.allocate,
          _.toShort
        )
      }
    )
  }

  private def commonBufferTests[T, A: ClassTag, B <: JBuffer, C <: Buffer[A, B]](
    harness: Harness[T],
    allocate: Int => IO[Exception, C],
    wrap: Array[A] => IO[Exception, C],
    wrap3: (Array[A], Int, Int) => IO[Exception, C],
    jAllocate: Int => B,
    f: Int => A
  ): T = {

    import harness._

    val initialCapacity = 10
    def initialValues   = Array(1, 2, 3).map(f)
    def zeroValues      = Array(0, 0, 0).map(f)
    val zeroValue       = f(0)

    section(
      test("apply") { () =>
        val apply = allocate(3).flatMap(_.array)
        assert(unsafeRun(apply).sameElements(zeroValues))
      },
      namedSection("wrap")(
        test("backed by an array") { () =>
          val wrapArray =
            for {
              buffer <- wrap(initialValues)
              array  <- buffer.array
            } yield array

          assert(unsafeRun(wrapArray).sameElements(initialValues))
        },
        namedSection("backed by array with offset and length") {

          val wrapIO = wrap3(initialValues, 1, 2)

          section(
            test("array") { () =>
              assert(unsafeRun(wrapIO.flatMap(_.array)).sameElements(initialValues))
            },
            test("position") { () =>
              assert(unsafeRun(wrapIO.flatMap(_.position)) == 1)
            },
            test("remaining") { () =>
              assert(unsafeRun(wrapIO.flatMap(_.remaining)) == 2)
            }
          )
        }
      ),
      namedSection("get")(
        test("at current position") { () =>
          val get = wrap3(initialValues, 1, 2).flatMap(_.get)
          assert(unsafeRun(get) == 2)
        },
        test("at index") { () =>
          val get = wrap(initialValues).flatMap(_.get(1))

          assert(unsafeRun(get) == 2)
        },
        test("should update position") { () =>
          val position =
            for {
              buffer <- wrap(initialValues)
              _      <- buffer.get
              pos    <- buffer.position
            } yield pos

          assert(unsafeRun(position) == 1)
        }
      ),
      namedSection("put")(
        test("at current position") { () =>
          val put = for {
            buffer <- wrap(initialValues)
            _      <- buffer.put(zeroValue)
            array  <- buffer.array
          } yield array

          assert(unsafeRun(put).sameElements(Array(0, 2, 3).map(f)))
        },
        test("at index") { () =>
          val put = for {
            buffer <- wrap(initialValues)
            _      <- buffer.put(1, zeroValue)
            array  <- buffer.array
          } yield array

          assert(unsafeRun(put).sameElements(Array(1, 0, 3).map(f)))
        }
      ),
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
        val newPosition = 3

        def position =
          for {
            b <- allocate(initialCapacity)
            _ <- b.position(newPosition)
          } yield b

        test("position set") { () =>
          val actual = unsafeRun(position.flatMap(_.position))
          assert(actual == newPosition)
        }
      },
      namedSection("limit") {
        val newLimit = 3

        section(
          test("limit set") { () =>
            val limit = for {
              b        <- allocate(initialCapacity)
              _        <- b.limit(newLimit)
              newLimit <- b.limit
            } yield newLimit

            assert(unsafeRun(limit) == newLimit)
          },
          test("position reset") { () =>
            val positionReset = for {
              b        <- allocate(initialCapacity)
              _        <- b.position(newLimit + 1)
              _        <- b.limit(newLimit)
              position <- b.position
            } yield position

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
      }
    )
  }
}
