package scalaz.nio

import scalaz.zio.IO
//import scalaz.Scalaz._

import java.nio.{
  ByteOrder,
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
//import scala.{Array => SArray}

//case class Array[A: ClassTag](private val array: SArray[A]) {
//  final def length = array.length

// Expose all methods in IO
//}

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag, B <: JBuffer] private[nio] (private[nio] val buffer: B) {

  final def capacity: IO[Nothing, Int] = IO.now(buffer.capacity)

  final def position: IO[Nothing, Int] = IO.now(buffer.position)

  final def position(newPosition: Int): IO[Exception, Unit] =
    IO.syncException(buffer.position(newPosition)).void

  final def limit: IO[Nothing, Int] = IO.now(buffer.limit)

  final def remaining: IO[Nothing, Int] = IO.now(buffer.remaining)

  final def hasRemaining: IO[Nothing, Boolean] = IO.now(buffer.hasRemaining)

  final def limit(newLimit: Int): IO[Exception, Unit] =
    IO.syncException(buffer.limit(newLimit)).void

  final def mark: IO[Nothing, Unit] = IO.sync(buffer.mark()).void

  final def reset: IO[Nothing, Unit] = IO.sync(buffer.reset()).void

  final def clear: IO[Nothing, Unit] = IO.sync(buffer.clear()).void

  final def flip: IO[Nothing, Unit] = IO.sync(buffer.flip()).void

  final def rewind: IO[Nothing, Unit] = IO.sync(buffer.rewind()).void

  final def isReadOnly: IO[Nothing, Boolean] = IO.now(buffer.isReadOnly)

  final def hasArray: IO[Nothing, Boolean] = IO.now(buffer.hasArray)

  final def arrayOffset: IO[Nothing, Int] = IO.now(buffer.arrayOffset)

  final def isDirect: IO[Nothing, Boolean] = IO.now(buffer.isDirect)

  // Following operations are present in every buffer, but for some reason are not part of Buffer interface.
  // To avoid repeating boilerplate code the implementations are provided by BufferOps macro annotation.

  type Self <: Buffer[A, B]

  def array: IO[Exception, Array[A]]

  def get: IO[Exception, A]

  def get(i: Int): IO[Exception, A]

  def put(element: A): IO[Exception, Self]

  def put(index: Int, element: A): IO[Exception, Self]

  def order: IO[Nothing, ByteOrder]

  def slice: IO[Exception, Self]

  def asReadOnlyBuffer: IO[Exception, Self]
}

object Buffer {

  def byte(capacity: Int)   = ByteBuffer(capacity)
  def char(capacity: Int)   = CharBuffer(capacity)
  def double(capacity: Int) = DoubleBuffer(capacity)
  def float(capacity: Int)  = FloatBuffer(capacity)
  def int(capacity: Int)    = IntBuffer(capacity)
  def long(capacity: Int)   = LongBuffer(capacity)
  def short(capacity: Int)  = ShortBuffer(capacity)
}

@BufferOps
class ByteBuffer private (private[nio] val javaBuffer: JByteBuffer)
    extends Buffer[Byte, JByteBuffer](javaBuffer) {

  def asCharBuffer: IO[Exception, CharBuffer] =
    IO.syncException(CharBuffer(javaBuffer.asCharBuffer()))

  def asDoubleBuffer: IO[Exception, DoubleBuffer] =
    IO.syncException(DoubleBuffer(javaBuffer.asDoubleBuffer()))

  def asFloatBuffer: IO[Exception, FloatBuffer] =
    IO.syncException(FloatBuffer(javaBuffer.asFloatBuffer()))

  def asIntBuffer: IO[Exception, IntBuffer] =
    IO.syncException(IntBuffer(javaBuffer.asIntBuffer()))

  def asLongBuffer: IO[Exception, LongBuffer] =
    IO.syncException(LongBuffer(javaBuffer.asLongBuffer()))

  def asShortBuffer: IO[Exception, ShortBuffer] =
    IO.syncException(ShortBuffer(javaBuffer.asShortBuffer()))
}

@BufferOps
class CharBuffer private (private[nio] val javaBuffer: JCharBuffer)
    extends Buffer[Char, JCharBuffer](javaBuffer)

@BufferOps
class DoubleBuffer private (private[nio] val javaBuffer: JDoubleBuffer)
    extends Buffer[Double, JDoubleBuffer](javaBuffer)

@BufferOps
class FloatBuffer private (private[nio] val javaBuffer: JFloatBuffer)
    extends Buffer[Float, JFloatBuffer](javaBuffer)

@BufferOps
class IntBuffer private (private[nio] val javaBuffer: JIntBuffer)
    extends Buffer[Int, JIntBuffer](javaBuffer)

@BufferOps
class LongBuffer private (private[nio] val javaBuffer: JLongBuffer)
    extends Buffer[Long, JLongBuffer](javaBuffer)

@BufferOps
class ShortBuffer private (private[nio] val javaBuffer: JShortBuffer)
    extends Buffer[Short, JShortBuffer](javaBuffer)
