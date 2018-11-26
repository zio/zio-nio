package scalaz.nio

import java.nio.ByteOrder
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
class ByteBuffer private (private val jByteBuffer: JByteBuffer)
    extends Buffer[Byte, JByteBuffer](jByteBuffer) {
  def array: IO[Exception, Array[Byte]] = IO.syncException(jByteBuffer.array())

  // ToDo: other as*Buffer methods can be added along with implementation of other *Buffer s
  def asCharBuffer: IO[Exception, CharBuffer] =
    IO.syncException(CharBuffer(jByteBuffer.asCharBuffer()))

  def asReadOnlyBuffer: IO[Exception, ByteBuffer] =
    IO.syncException(ByteBuffer(jByteBuffer.asReadOnlyBuffer()))

  def duplicate: IO[Exception, ByteBuffer] = IO.syncException(ByteBuffer(jByteBuffer.duplicate()))

  // ToDo: java api returns this buffer, scalaz api returns Unit to emphaisze that this operation has side-effect (e.g. transering bytes into dst, changing buffer's position)
  def get(dst: Array[Byte]): IO[Exception, Unit] = IO.syncException(jByteBuffer.get(dst)).void

  def get(dst: Array[Byte], offset: Int, length: Int): IO[Exception, Unit] =
    IO.syncException(jByteBuffer.get(dst, offset, length)).void

  def get[T](implicit relativeGet: RelativeGet[T]): IO[Exception, T] =
    IO.syncException(relativeGet.get)

  def get[T](index: Int)(implicit absoluteGet: AbsoluteGet[T]): IO[Exception, T] =
    IO.syncException(absoluteGet.get(index))

  def order: IO[Nothing, ByteOrder] = IO.now(jByteBuffer.order)

  def order(byteOrder: ByteOrder): IO[Nothing, Unit] = IO.sync(jByteBuffer.order(byteOrder)).void

  def put(src: Array[Byte]): IO[Exception, Unit] = IO.syncException(jByteBuffer.put(src)).void

  def put(src: Array[Byte], offset: Int, length: Int): IO[Exception, Unit] =
    IO.syncException(jByteBuffer.put(src, offset, length)).void

  def put(src: ByteBuffer): IO[Exception, Unit] =
    IO.syncException(jByteBuffer.put(src.jByteBuffer)).void

  def put[T](value: T)(implicit relativePut: RelativePut[T]): IO[Exception, Unit] =
    IO.syncException(relativePut.put(value))

  def put[T](index: Int, value: T)(implicit absolutePut: AbsolutePut[T]): IO[Exception, Unit] =
    IO.syncException(absolutePut.put(index, value))

  def slice: IO[Exception, ByteBuffer] = IO.syncException(ByteBuffer(jByteBuffer.slice))

  // ToDo: can deriviation be used to generate instances of this type classes? macross?
  trait RelativeGet[T] {
    def get: T
  }

  trait AbsoluteGet[T] {
    def get(index: Int): T
  }

  trait RelativePut[T] {
    def put(value: T): Unit
  }

  trait AbsolutePut[T] {
    def put(index: Int, value: T): Unit
  }

  implicit val byteRelativeGet: RelativeGet[Byte] = new RelativeGet[Byte] {
    def get = jByteBuffer.get()
  }

  implicit val charRelativeGet: RelativeGet[Char] = new RelativeGet[Char] {
    def get = jByteBuffer.getChar()
  }

  implicit val doubleRelativeGet: RelativeGet[Double] = new RelativeGet[Double] {
    def get = jByteBuffer.getDouble()
  }

  implicit val floatRelativeGet: RelativeGet[Float] = new RelativeGet[Float] {
    def get = jByteBuffer.getFloat()
  }

  implicit val intRelativeGet: RelativeGet[Int] = new RelativeGet[Int] {
    def get = jByteBuffer.getInt()
  }

  implicit val longRelativeGet: RelativeGet[Long] = new RelativeGet[Long] {
    def get = jByteBuffer.getLong()
  }

  implicit val shortRelativeGet: RelativeGet[Short] = new RelativeGet[Short] {
    def get = jByteBuffer.getShort()
  }

  implicit val byteIndexReader: AbsoluteGet[Byte] = new AbsoluteGet[Byte] {
    def get(index: Int) = jByteBuffer.get(index)
  }

  implicit val charIndexReader: AbsoluteGet[Char] = new AbsoluteGet[Char] {
    def get(index: Int) = jByteBuffer.getChar(index)
  }

  implicit val doubleIndexReader: AbsoluteGet[Double] = new AbsoluteGet[Double] {
    def get(index: Int) = jByteBuffer.getDouble(index)
  }

  implicit val floatIndexReader: AbsoluteGet[Float] = new AbsoluteGet[Float] {
    def get(index: Int) = jByteBuffer.getFloat(index)
  }

  implicit val intIndexReader: AbsoluteGet[Int] = new AbsoluteGet[Int] {
    def get(index: Int) = jByteBuffer.getInt(index)
  }

  implicit val longIndexReader: AbsoluteGet[Long] = new AbsoluteGet[Long] {
    def get(index: Int) = jByteBuffer.getLong(index)
  }

  implicit val shortIndexReader: AbsoluteGet[Short] = new AbsoluteGet[Short] {
    def get(index: Int) = jByteBuffer.getShort(index)
  }

  implicit val byteRelativePut: RelativePut[Byte] = new RelativePut[Byte] {

    def put(value: Byte): Unit = {
      jByteBuffer.put(value)
      ()
    }
  }

  implicit val charRelativePut: RelativePut[Char] = new RelativePut[Char] {

    def put(value: Char): Unit = {
      jByteBuffer.putChar(value)
      ()
    }
  }

  implicit val dboubleRelativePut: RelativePut[Double] = new RelativePut[Double] {

    def put(value: Double): Unit = {
      jByteBuffer.putDouble(value)
      ()
    }
  }

  implicit val floatRelativePut: RelativePut[Float] = new RelativePut[Float] {

    def put(value: Float): Unit = {
      jByteBuffer.putFloat(value)
      ()
    }
  }

  implicit val intRelativePut: RelativePut[Int] = new RelativePut[Int] {

    def put(value: Int): Unit = {
      jByteBuffer.putInt(value)
      ()
    }
  }

  implicit val longRelativePut: RelativePut[Long] = new RelativePut[Long] {

    def put(t: Long): Unit = {
      jByteBuffer.putLong(t)
      ()
    }
  }

  implicit val shortRelativePut: RelativePut[Short] = new RelativePut[Short] {

    def put(t: Short): Unit = {
      jByteBuffer.putShort(t)
      ()
    }
  }

  implicit val byteAbsolutePut: AbsolutePut[Byte] = new AbsolutePut[Byte] {

    def put(index: Int, value: Byte): Unit = {
      jByteBuffer.put(index, value)
      ()
    }
  }

  implicit val charAbsolutePut: AbsolutePut[Char] = new AbsolutePut[Char] {

    def put(index: Int, value: Char): Unit = {
      jByteBuffer.putChar(index, value)
      ()
    }
  }

  implicit val doubleAbsolutePut: AbsolutePut[Double] = new AbsolutePut[Double] {

    def put(index: Int, value: Double): Unit = {
      jByteBuffer.putDouble(index, value)
      ()
    }
  }

  implicit val floatAbsolutePut: AbsolutePut[Float] = new AbsolutePut[Float] {

    def put(index: Int, value: Float): Unit = {
      jByteBuffer.putFloat(index, value)
      ()
    }
  }

  implicit val intAbsolutePut: AbsolutePut[Int] = new AbsolutePut[Int] {

    def put(index: Int, value: Int): Unit = {
      jByteBuffer.putInt(index, value)
      ()
    }
  }

  implicit val longAbsolutePut: AbsolutePut[Long] = new AbsolutePut[Long] {

    def put(index: Int, value: Long): Unit = {
      jByteBuffer.putLong(index, value)
      ()
    }
  }

  implicit val shortAbsolutePut: AbsolutePut[Short] = new AbsolutePut[Short] {

    def put(index: Int, value: Short): Unit = {
      jByteBuffer.putShort(index, value)
      ()
    }
  }

}

object ByteBuffer {

  private[nio] def apply(charBuffer: JByteBuffer): ByteBuffer = new ByteBuffer(charBuffer)

  def apply(capacity: Int): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.allocate(capacity)).map(new ByteBuffer(_))

  def apply(array: Array[Byte]): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.wrap(array)).map(new ByteBuffer(_))

  def apply(array: Array[Byte], offset: Int, length: Int): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.wrap(array, offset, length)).map(new ByteBuffer(_))
}

class CharBuffer private (val charBuffer: JCharBuffer)
    extends Buffer[Char, JCharBuffer](charBuffer) {

  def array: IO[Exception, Array[Char]] =
    IO.syncException(charBuffer.array().asInstanceOf[Array[Char]])
}

object CharBuffer {

  private[nio] def apply(charBuffer: JCharBuffer): CharBuffer = new CharBuffer(charBuffer)

  def apply(capacity: Int): IO[Exception, CharBuffer] =
    IO.syncException(JCharBuffer.allocate(capacity)).map(new CharBuffer(_))
}
