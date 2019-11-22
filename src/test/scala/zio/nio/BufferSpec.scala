package zio.nio

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

import scala.reflect.ClassTag

import zio.{ Chunk, IO }
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import BufferSpecUtils._

object BufferSpec
    extends BaseSpec(
      suite("BufferSpec")(
        commonBufferTests(
          "ByteBuffer",
          Buffer.byte,
          Buffer.byte,
          JByteBuffer.allocate,
          _.toByte
        ),
        commonBufferTests(
          "CharBuffer",
          Buffer.char,
          Buffer.char,
          JCharBuffer.allocate,
          _.toChar
        ),
        commonBufferTests(
          "DoubleBuffer",
          Buffer.double,
          Buffer.double,
          JDoubleBuffer.allocate,
          _.toDouble
        ),
        commonBufferTests(
          "FloatBuffer",
          Buffer.float,
          Buffer.float,
          JFloatBuffer.allocate,
          _.toFloat
        ),
        commonBufferTests(
          "IntBuffer",
          Buffer.int,
          Buffer.int,
          JIntBuffer.allocate,
          identity
        ),
        commonBufferTests(
          "LongBuffer",
          Buffer.long,
          Buffer.long,
          JLongBuffer.allocate,
          _.toLong
        ),
        commonBufferTests(
          "ShortBuffer",
          Buffer.short,
          Buffer.short,
          JShortBuffer.allocate,
          _.toShort
        )
      )
    )

object BufferSpecUtils {

  def commonBufferTests[T, A: ClassTag, B <: JBuffer, C <: Buffer[A]](
    suiteName: String,
    allocate: Int => IO[Exception, C],
    wrap: Chunk[A] => IO[Exception, C],
    jAllocate: Int => B,
    f: Int => A
  ): ZSpec[TestEnvironment, Exception, String, Unit] = {
    val initialCapacity = 10
    def initialValues   = Array(1, 2, 3).map(f)
    def zeroValues      = Array(0, 0, 0).map(f)

    suite(suiteName)(
      testM("apply") {
        for {
          allocated <- allocate(3)
          array     <- allocated.array
        } yield assert(array.sameElements(zeroValues), isTrue)
      },
      testM("wrap backed by an array") {
        for {
          buffer <- wrap(Chunk.fromArray(initialValues))
          array  <- buffer.array
        } yield assert(array.sameElements(initialValues), isTrue)
      },
      testM("capacity") {
        for {
          allocated <- allocate(initialCapacity)
          capacity  = allocated.capacity
        } yield assert(capacity == jAllocate(initialCapacity).capacity, isTrue)
      },
      testM("capacity initialized") {
        for {
          allocated <- allocate(initialCapacity)
          capacity  = allocated.capacity
        } yield assert(capacity == initialCapacity, isTrue)
      },
      testM("position is 0") {
        for {
          allocated <- allocate(initialCapacity)
          position  <- allocated.position
        } yield assert(position == 0, isTrue)
      },
      testM("limit is capacity") {
        for {
          allocated <- allocate(initialCapacity)
          limit     <- allocated.limit
        } yield assert(limit == initialCapacity, isTrue)
      },
      testM("position set") {
        for {
          buffer   <- Buffer.byte(initialCapacity)
          _        <- buffer.position(3)
          position <- buffer.position
        } yield assert(position == 3, isTrue)
      },
      testM("limit set") {
        for {
          buffer   <- Buffer.byte(initialCapacity)
          limit    = 3
          _        <- buffer.limit(limit)
          newLimit <- buffer.limit
        } yield assert(newLimit == limit, isTrue)
      },
      testM("position reset") {
        for {
          buffer   <- Buffer.byte(initialCapacity)
          newLimit = 3
          _        <- buffer.position(newLimit + 1)
          _        <- buffer.limit(newLimit)
          position <- buffer.position
        } yield assert(position == newLimit, isTrue)
      },
      testM("reset to marked position") {
        for {
          b           <- allocate(initialCapacity)
          _           <- b.position(1)
          _           <- b.mark
          _           <- b.position(2)
          _           <- b.reset
          newPosition <- b.position
        } yield assert(newPosition == 1, isTrue)
      },
      testM("clear") {
        for {
          b        <- allocate(initialCapacity)
          _        <- b.position(1)
          _        <- b.mark
          _        <- b.clear
          position <- b.position
          limit    <- b.limit
        } yield assert(position == 0 && limit == initialCapacity, isTrue)
      },
      testM("flip") {
        for {
          b        <- allocate(initialCapacity)
          _        <- b.position(1)
          _        <- b.flip
          position <- b.position
          limit    <- b.limit
        } yield assert(position == 0 && limit == 1, isTrue)
      },
      testM("rewind sets position to 0") {
        for {
          b           <- allocate(initialCapacity)
          _           <- b.position(1)
          _           <- b.rewind
          newPosition <- b.position
        } yield assert(newPosition == 0, isTrue)
      },
      testM("heap buffers a backed by an array") {
        for {
          b <- allocate(initialCapacity)
        } yield assert(b.hasArray, isTrue)
      },
      testM[TestEnvironment, Exception, String]("0 <= mark <= position <= limit <= capacity") {
        checkM(Gen.int(-1, 10), Gen.int(-1, 10), Gen.int(-1, 10), Gen.int(-1, 10)) {
          (markedPosition: Int, position: Int, limit: Int, capacity: Int) =>
            (for {
              b    <- Buffer.byte(capacity)
              _    <- b.limit(limit)
              _    <- b.position(markedPosition)
              _    <- b.mark
              _    <- b.position(position)
              _    <- b.reset
              mark <- b.position
            } yield assert(0 <= mark && mark <= position && position <= limit && limit <= capacity, isTrue))
              .catchSome {
                case _: IllegalArgumentException | _: IllegalStateException =>
                  IO.effectTotal(assert(true, isTrue))
              }
        }
      }
    )
  }
}
