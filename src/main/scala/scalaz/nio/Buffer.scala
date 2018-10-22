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

  def get: A

  final def array: IO[Exception, Maybe[Array[A]]] =
    IO.syncException {
      // todo: avoid explicit casting
      val a: Array[A] = buffer.array().asInstanceOf[Array[A]]

      Maybe.fromNullable(a)
    }

  final def hasArray: IO[Nothing, Boolean] = IO.now(buffer.hasArray)
  final def arrayOffset: IO[Nothing, Int]  = IO.now(buffer.arrayOffset)
  final def isDirect: IO[Nothing, Boolean] = IO.now(buffer.isDirect)

}

object Buffer {
  def byte(capacity: Int) = ByteBuffer(capacity)

  def char(capacity: Int) = CharBuffer(capacity)

  private class ByteBuffer private (val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {
    def get: Byte = byteBuffer.array().asInstanceOf[Byte]
  }

  private class CharBuffer private (private val charBuffer: JByteBuffer)
      extends Buffer[Char](charBuffer) {
    def get: Char = charBuffer.array().asInstanceOf[Char]
  }

  private object ByteBuffer {

    def apply(capacity: Int): IO[Exception, Buffer[Byte]] =
      IO.syncException(JByteBuffer.allocate(capacity)).map(new ByteBuffer(_))
  }

  private object CharBuffer {

    def apply(capacity: Int): Buffer[Char] =
      new CharBuffer(JByteBuffer.allocate(capacity))
  }

}
