package scalaz.nio

import scalaz.zio.IO

import scalaz._
//import scalaz.Scalaz._

import java.nio.{ Buffer => JBuffer }
import java.nio.{ ByteBuffer => JByteBuffer }
import scala.reflect.ClassTag
//import scala.{Array => SArray}

//case class Array[A: ClassTag](private val array: SArray[A]) {
//  final def length = array.length

// Expose all methods in IO
//}

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private (val buffer: JBuffer) {
  final def capacity: Int = buffer.capacity

  // should it return Unit?
  final def clear: IO[Nothing, Buffer[A]] = IO.sync(buffer.clear().asInstanceOf[Buffer[A]])

  def get: A

  final def array: IO[Exception, Maybe[Array[A]]] =
    IO.syncException {
      // todo: avoid explicit casting
      val a: Array[A] = buffer.array().asInstanceOf[Array[A]]

      Maybe.fromNullable(a)
    }

}

object Buffer {
  def byte(capacity: Int): Buffer[Byte] = ByteBuffer(capacity)
  def char(capacity: Int): Buffer[Char] = CharBuffer(capacity)

  private class ByteBuffer private (val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {
    def get: Byte = byteBuffer.array().asInstanceOf[Byte]
  }

  private class CharBuffer private (private val charBuffer: JByteBuffer)
      extends Buffer[Char](charBuffer) {
    def get: Char = charBuffer.array().asInstanceOf[Char]
  }

  private object ByteBuffer {
    def apply(capacity: Int): Buffer[Byte] = new ByteBuffer(JByteBuffer.allocate(capacity))
  }

  private object CharBuffer {
    def apply(capacity: Int): Buffer[Char] = new CharBuffer(JByteBuffer.allocate(capacity))
  }

}
