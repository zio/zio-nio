package scalaz.nio

import scalaz.zio.IO
//import scalaz.Scalaz._

import java.nio.{ Buffer => JBuffer, ByteBuffer => JByteBuffer }

import scala.reflect.ClassTag
//import scala.{Array => SArray}

//case class Array[A: ClassTag](private val array: SArray[A]) {
//  final def length = array.length

// Expose all methods in IO
//}

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag, B <: JBuffer] private[nio] (private[nio] val buffer: B) {
  final def capacity: IO[Nothing, Int] = IO.succeed(buffer.capacity)

  final def position: IO[Nothing, Int] = IO.succeed(buffer.position)

  final def position(newPosition: Int): IO[Exception, Unit] =
    IO.syncException(buffer.position(newPosition)).void

  final def limit: IO[Nothing, Int] = IO.succeed(buffer.limit)

  final def remaining: IO[Nothing, Int] = IO.succeed(buffer.remaining)

  final def hasRemaining: IO[Nothing, Boolean] = IO.succeed(buffer.hasRemaining)

  final def limit(newLimit: Int): IO[Exception, Unit] =
    IO.syncException(buffer.limit(newLimit)).void

  final def mark: IO[Nothing, Unit] = IO.sync(buffer.mark()).void

  final def reset: IO[Exception, Unit] =
    IO.syncException(buffer.reset()).void

  final def clear: IO[Nothing, Unit] = IO.sync(buffer.clear()).void

  final def flip: IO[Nothing, Unit] = IO.sync(buffer.flip()).void

  final def rewind: IO[Nothing, Unit] = IO.sync(buffer.rewind()).void

  final def isReadOnly: IO[Nothing, Boolean] = IO.succeed(buffer.isReadOnly)

  def array: IO[Exception, Array[A]]

  final def hasArray: IO[Nothing, Boolean] = IO.succeed(buffer.hasArray)
  final def arrayOffset: IO[Nothing, Int]  = IO.succeed(buffer.arrayOffset)
  final def isDirect: IO[Nothing, Boolean] = IO.succeed(buffer.isDirect)

}

class ByteBuffer private (val byteBuffer: JByteBuffer)
    extends Buffer[Byte, JByteBuffer](byteBuffer) {
  def array: IO[Exception, Array[Byte]] = IO.syncException(byteBuffer.array())
}

object ByteBuffer {

  def apply(capacity: Int): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.allocate(capacity)).map(new ByteBuffer(_))

  def apply(bytes: Seq[Byte]): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.wrap(bytes.toArray)).map(new ByteBuffer(_))
}

object Buffer {
  def byte(capacity: Int) = ByteBuffer(capacity)

  def char(capacity: Int) = CharBuffer(capacity)

  private class CharBuffer private (private val charBuffer: JByteBuffer)
      extends Buffer[Char, JByteBuffer](charBuffer) {

    def array: IO[Exception, Array[Char]] =
      IO.syncException(charBuffer.array().asInstanceOf[Array[Char]])
  }

  private object CharBuffer {

    def apply(capacity: Int): Buffer[Char, JByteBuffer] =
      new CharBuffer(JByteBuffer.allocate(capacity))
  }

}
