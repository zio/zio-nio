package scalaz.nio

import scalaz.zio.IO
//import scalaz.Scalaz._

import java.nio.{ Buffer => JBuffer, ByteBuffer => JByteBuffer, CharBuffer => JCharBuffer }

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

  def array: IO[Exception, Array[A]]

  final def hasArray: IO[Nothing, Boolean] = IO.now(buffer.hasArray)
  final def arrayOffset: IO[Nothing, Int]  = IO.now(buffer.arrayOffset)
  final def isDirect: IO[Nothing, Boolean] = IO.now(buffer.isDirect)

}

object Buffer {
  def byte(capacity: Int) = ByteBuffer(capacity)

  def char(capacity: Int) = CharBuffer(capacity)
}

// ToDo: In current implementation only non-direct buffers are supported
class ByteBuffer private (val byteBuffer: JByteBuffer)
    extends Buffer[Byte, JByteBuffer](byteBuffer) {
  def array: IO[Exception, Array[Byte]] = IO.syncException(byteBuffer.array())

  def asCharBuffer: IO[Exception, CharBuffer] = IO.syncException(CharBuffer(byteBuffer.asCharBuffer()))

  def asReadOnlyBuffer: IO[Exception, ByteBuffer] = IO.syncException(ByteBuffer(byteBuffer.asReadOnlyBuffer()))

  def duplicate: IO[Exception, ByteBuffer] = IO.syncException(ByteBuffer(byteBuffer.duplicate()))

  def get: IO[Exception, Byte] = IO.syncException(byteBuffer.get())

  // ToDo: java api returns this buffer, scalaz api returns Unit to emphaisze that this operation has side-effect (e.g. transering bytes into dst, changing buffer's position)
  def get(dst: Array[Byte]): IO[Exception, Unit] = IO.syncException(byteBuffer.get(dst)).void

  def get(dst: Array[Byte], offset: Int, length: Int): IO[Exception, Unit] = IO.syncException(byteBuffer.get(dst, offset, length)).void

  def get(index: Int): IO[Exception, Byte] = IO.syncException(byteBuffer.get(index))

  def get[T](implicit read: Read[T]): IO[Exception, T] = IO.syncException(read.value)

  def get[T](index: Int)(implicit readIndex: ReadIndex[T]): IO[Exception, T] = IO.syncException(readIndex.value(index))

  trait Read[T] {
    def value: T
  }

  trait ReadIndex[T] {
    def value(index: Int): T
  }

  implicit val charReader: Read[Char] = new Read[Char] {
    def value = byteBuffer.getChar()
  }

  implicit val doubleReader: Read[Double] = new Read[Double] {
    def value = byteBuffer.getDouble()
  }

  implicit val charIndexReader: ReadIndex[Char] = new ReadIndex[Char] {
    def value(index: Int) = byteBuffer.getChar(index)
  }

  implicit val doubleIndexReader: ReadIndex[Double] = new ReadIndex[Double] {
    def value(index: Int) = byteBuffer.getDouble(index)
  }

// Absolute get method for reading a char value.
// abstract DoublegetDouble()
// Relative get method for reading a double value.
// abstract DoublegetDouble(int index)
// Absolute get method for reading a double value.
// abstract FloatgetFloat()
// Relative get method for reading a float value.
// abstract FloatgetFloat(int index)
// Absolute get method for reading a float value.
// abstract IntgetInt()
// Relative get method for reading an int value.
// abstract IntgetInt(int index)
// Absolute get method for reading an int value.
// abstract LonggetLong()
// Relative get method for reading a long value.
// abstract LonggetLong(int index)
// Absolute get method for reading a long value.
// abstract ShortgetShort()
// Relative get method for reading a short value.
// abstract ShortgetShort(int index)
// Absolute get method for reading a short value.

}

object ByteBuffer {

  private[nio] def apply (charBuffer: JByteBuffer): ByteBuffer = new ByteBuffer(charBuffer)

  def apply(capacity: Int): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.allocate(capacity)).map(new ByteBuffer(_))
}

class CharBuffer private (val charBuffer: JCharBuffer)
    extends Buffer[Char, JCharBuffer](charBuffer) {

  def array: IO[Exception, Array[Char]] =
    IO.syncException(charBuffer.array().asInstanceOf[Array[Char]])
}

object CharBuffer {

  private[nio] def apply (charBuffer: JCharBuffer): CharBuffer = new CharBuffer(charBuffer)

  def apply(capacity: Int): IO[Exception, CharBuffer] =
    IO.syncException(JCharBuffer.allocate(capacity)).map(new CharBuffer(_))
}
