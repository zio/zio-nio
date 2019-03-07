package scalaz.nio

import java.nio.{
  Buffer => JBuffer,
  ByteBuffer => JByteBuffer,
  CharBuffer => JCharBuffer,
  FloatBuffer => JFloatBuffer,
  DoubleBuffer => JDoubleBuffer,
  IntBuffer => JIntBuffer,
  LongBuffer => JLongBuffer,
  ShortBuffer => JShortBuffer
}
import scalaz.zio.{ Chunk, IO }

import scala.reflect.ClassTag

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private[nio] (private[nio] val buffer: JBuffer) {
  final val capacity: IO[Nothing, Int] = IO.succeed(buffer.capacity)

  final def position: IO[Nothing, Int] = IO.sync(buffer.position)

  final def position(newPosition: Int): IO[Exception, Unit] =
    IO.syncException(buffer.position(newPosition)).void

  final def limit: IO[Nothing, Int] = IO.sync(buffer.limit)

  final def remaining: IO[Nothing, Int] = IO.sync(buffer.remaining)

  final def hasRemaining: IO[Nothing, Boolean] = IO.sync(buffer.hasRemaining)

  final def limit(newLimit: Int): IO[Exception, Unit] =
    IO.syncException(buffer.limit(newLimit)).void

  final def mark: IO[Nothing, Unit] = IO.sync(buffer.mark()).void

  final def reset: IO[Exception, Unit] =
    IO.syncException(buffer.reset()).void

  final def clear: IO[Nothing, Unit] = IO.sync(buffer.clear()).void

  final def flip: IO[Nothing, Unit] = IO.sync(buffer.flip()).void

  final def rewind: IO[Nothing, Unit] = IO.sync(buffer.rewind()).void

  final val isReadOnly: IO[Nothing, Boolean] = IO.succeed(buffer.isReadOnly)

  final val hasArray: IO[Nothing, Boolean] = IO.succeed(buffer.hasArray)

  final def arrayOffset: IO[Exception, Int] = IO.syncException(buffer.arrayOffset)

  final val isDirect: IO[Nothing, Boolean] = IO.succeed(buffer.isDirect)

  def array: IO[Exception, Array[A]]

  def get: IO[Exception, A]

  def get(i: Int): IO[Exception, A]

  def put(element: A): IO[Exception, Buffer[A]]

  def put(index: Int, element: A): IO[Exception, Buffer[A]]

  def asReadOnlyBuffer: IO[Exception, Buffer[A]]

}

object Buffer {

  def byte(capacity: Int): IO[Exception, Buffer[Byte]] =
    IO.syncException(JByteBuffer.allocate(capacity)).map(new ByteBuffer(_))

  def byte(chunk: Chunk[Byte]): IO[Exception, Buffer[Byte]] =
    IO.syncException(JByteBuffer.wrap(chunk.toArray)).map(new ByteBuffer(_))

  def char(capacity: Int): IO[Exception, Buffer[Char]] =
    IO.syncException(JCharBuffer.allocate(capacity)).map(new CharBuffer(_))

  def char(chunk: Chunk[Char]): IO[Exception, Buffer[Char]] =
    IO.syncException(JCharBuffer.wrap(chunk.toArray)).map(new CharBuffer(_))

  def float(capacity: Int): IO[Exception, Buffer[Float]] =
    IO.syncException(JFloatBuffer.allocate(capacity)).map(new FloatBuffer(_))

  def float(chunk: Chunk[Float]): IO[Exception, Buffer[Float]] =
    IO.syncException(JFloatBuffer.wrap(chunk.toArray)).map(new FloatBuffer(_))

  def double(capacity: Int): IO[Exception, Buffer[Double]] =
    IO.syncException(JDoubleBuffer.allocate(capacity)).map(new DoubleBuffer(_))

  def double(chunk: Chunk[Double]): IO[Exception, Buffer[Double]] =
    IO.syncException(JDoubleBuffer.wrap(chunk.toArray)).map(new DoubleBuffer(_))

  def int(capacity: Int): IO[Exception, Buffer[Int]] =
    IO.syncException(JIntBuffer.allocate(capacity)).map(new IntBuffer(_))

  def int(chunk: Chunk[Int]): IO[Exception, Buffer[Int]] =
    IO.syncException(JIntBuffer.wrap(chunk.toArray)).map(new IntBuffer(_))

  def long(capacity: Int): IO[Exception, Buffer[Long]] =
    IO.syncException(JLongBuffer.allocate(capacity)).map(new LongBuffer(_))

  def long(chunk: Chunk[Long]): IO[Exception, Buffer[Long]] =
    IO.syncException(JLongBuffer.wrap(chunk.toArray)).map(new LongBuffer(_))

  def short(capacity: Int): IO[Exception, Buffer[Short]] =
    IO.syncException(JShortBuffer.allocate(capacity)).map(new ShortBuffer(_))

  def short(chunk: Chunk[Short]): IO[Exception, Buffer[Short]] =
    IO.syncException(JShortBuffer.wrap(chunk.toArray)).map(new ShortBuffer(_))
}
