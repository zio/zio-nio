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

import zio.nio.channels.ZIOBaseSpec
import zio.{ Chunk, IO }
import zio.test._
import zio.test.Assertion._

import scala.reflect.ClassTag
import BufferSpecUtils._
import zio.test.mock.MockEnvironment

object BufferSpec
    extends ZIOBaseSpec(
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
  ): Spec[String, ZTest[MockEnvironment, Exception, Unit]] = {

    val initialCapacity = 10

    def initialValues = Array(1, 2, 3).map(f)

    def zeroValues = Array(0, 0, 0).map(f)

    suite(suiteName)(
      testM[MockEnvironment, Exception, String]("apply") {
        for {
          apply <- allocate(3).flatMap(_.array)
        } yield assert(apply.sameElements(zeroValues), isTrue)
      },
      testM[MockEnvironment, Exception, String]("wrap backed by an array") {
        for {
          buffer <- wrap(Chunk.fromArray(initialValues))
          array  <- buffer.array
        } yield assert(array.sameElements(initialValues), isTrue)
      },
      testM[MockEnvironment, Exception, String]("capacity") {
        for {
          capacity <- allocate(initialCapacity).map(_.capacity)
        } yield assert(capacity == jAllocate(initialCapacity).capacity, isTrue)
      },
      testM[MockEnvironment, Exception, String]("capacity initialized") {
        for {
          capacity <- allocate(initialCapacity).map(_.capacity)
        } yield assert(capacity == initialCapacity, isTrue)
      },
      testM[MockEnvironment, Exception, String]("position is 0") {
        for {
          position <- allocate(initialCapacity).flatMap(_.position)
        } yield assert(position == 0, isTrue)
      },
      testM[MockEnvironment, Exception, String]("limit is capacity") {
        for {
          limit <- allocate(initialCapacity).flatMap(_.limit)
        } yield assert(limit == initialCapacity, isTrue)
      },
      testM[MockEnvironment, Exception, String]("position set") {

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
        } yield assert(newPosition == 1, isTrue)
      },
      testM("heap buffers a backed by an array") {
        for {
          b <- allocate(initialCapacity)
        } yield assert(b.hasArray, isTrue)

      },
      testM[MockEnvironment, Exception, String]("0 <= mark <= position <= limit <= capacity") {
        checkM(Gen.anyInt, Gen.anyInt, Gen.anyInt, Gen.anyInt) {
          (markedPosition: Int, position: Int, limit: Int, capacity: Int) =>
            for {
              b    <- Buffer.byte(capacity)
              _    <- b.limit(limit)
              _    <- b.position(markedPosition)
              _    <- b.mark
              _    <- b.position(position)
              _    <- b.reset
              mark <- b.position
            } yield assert(0 <= mark && mark <= position && position <= limit && limit <= capacity, isTrue)
        }
      }
    )
  }
}
