package zio.nio.core

import java.nio.{
  BufferUnderflowException,
  ByteOrder,
  ReadOnlyBufferException,
  Buffer => JBuffer,
  ByteBuffer => JByteBuffer,
  CharBuffer => JCharBuffer,
  DoubleBuffer => JDoubleBuffer,
  FloatBuffer => JFloatBuffer,
  IntBuffer => JIntBuffer,
  LongBuffer => JLongBuffer,
  ShortBuffer => JShortBuffer
}

import zio.{ Chunk, IO, UIO, ZIO }

import scala.reflect.ClassTag

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private[nio] (private[nio] val buffer: JBuffer) {
  final def capacity: Int = buffer.capacity

  def order: ByteOrder

  final def position: UIO[Int] = IO.effectTotal(buffer.position)

  final def position(newPosition: Int): IO[Exception, Unit] =
    IO.effect(buffer.position(newPosition)).unit.refineToOrDie[Exception]

  final def limit: UIO[Int] = IO.effectTotal(buffer.limit)

  final def remaining: UIO[Int] = IO.effectTotal(buffer.remaining)

  final def hasRemaining: UIO[Boolean] = IO.effectTotal(buffer.hasRemaining)

  final def limit(newLimit: Int): IO[Exception, Unit] =
    IO.effect(buffer.limit(newLimit)).unit.refineToOrDie[Exception]

  final def mark: UIO[Unit] = IO.effectTotal(buffer.mark()).unit

  final def reset: IO[Exception, Unit] =
    IO.effect(buffer.reset()).unit.refineToOrDie[Exception]

  final def clear: UIO[Unit] = IO.effectTotal(buffer.clear()).unit

  final def flip: UIO[Unit] = IO.effectTotal(buffer.flip()).unit

  final def rewind: UIO[Unit] = IO.effectTotal(buffer.rewind()).unit

  final def isReadOnly: Boolean = buffer.isReadOnly

  final def hasArray: Boolean = buffer.hasArray

  final def isDirect: Boolean = buffer.isDirect

  def slice: IO[Nothing, Buffer[A]]

  def compact: IO[ReadOnlyBufferException, Unit]

  def duplicate: IO[Nothing, Buffer[A]]

  final def withArray[R, E, B](
    noArray: ZIO[R, E, B]
  )(
    hasArray: (Array[A], Int) => ZIO[R, E, B]
  ): ZIO[R, E, B] =
    if (buffer.hasArray)
      for {
        a      <- array.orDie
        offset <- IO.effect(buffer.arrayOffset()).orDie
        result <- hasArray(a, offset)
      } yield result
    else
      noArray

  protected[nio] def array: IO[Exception, Array[A]]

  def get: IO[BufferUnderflowException, A]

  def get(i: Int): IO[IndexOutOfBoundsException, A]

  def getChunk(maxLength: Int = Int.MaxValue): IO[BufferUnderflowException, Chunk[A]]

  def put(element: A): IO[Exception, Unit]

  def put(index: Int, element: A): IO[Exception, Unit]

  def putChunk(chunk: Chunk[A]): IO[Exception, Unit]

  /**
   * Writes as much of a chunk as possible to this buffer.
   *
   * @return The remaining elements that could not fit in this buffer, if any.
   */
  final def fillFromChunk(chunk: Chunk[A]): IO[Nothing, Chunk[A]] =
    for {
      r                         <- remaining
      (putChunk, remainderChunk) = chunk.splitAt(r)
      _                         <- this.putChunk(putChunk).orDie
    } yield remainderChunk

  def asReadOnlyBuffer: IO[Nothing, Buffer[A]]
}

object Buffer {

  def byte(capacity: Int): IO[IllegalArgumentException, ByteBuffer] =
    IO.effect(JByteBuffer.allocate(capacity))
      .map(new ByteBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def byte(chunk: Chunk[Byte]): IO[Nothing, ByteBuffer] =
    IO.effectTotal(JByteBuffer.wrap(chunk.toArray)).map(new ByteBuffer(_))

  def byteDirect(capacity: Int): IO[IllegalArgumentException, ByteBuffer] =
    IO.effect(new ByteBuffer(JByteBuffer.allocateDirect(capacity)))
      .refineToOrDie[IllegalArgumentException]

  def byteFromJava(javaBuffer: JByteBuffer): ByteBuffer = new ByteBuffer(javaBuffer)

  def char(capacity: Int): IO[IllegalArgumentException, CharBuffer] =
    IO.effect(JCharBuffer.allocate(capacity))
      .map(new CharBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def char(chunk: Chunk[Char]): IO[Nothing, CharBuffer] =
    IO.effectTotal(JCharBuffer.wrap(chunk.toArray)).map(new CharBuffer(_))

  def char(
    charSequence: CharSequence,
    start: Int,
    end: Int
  ): IO[IndexOutOfBoundsException, CharBuffer] =
    IO.effect(new CharBuffer(JCharBuffer.wrap(charSequence, start, end)))
      .refineToOrDie[IndexOutOfBoundsException]

  def char(charSequence: CharSequence): IO[Nothing, CharBuffer] =
    IO.effectTotal(new CharBuffer(JCharBuffer.wrap(charSequence)))

  def charFromJava(javaBuffer: JCharBuffer): CharBuffer = new CharBuffer(javaBuffer)

  def float(capacity: Int): IO[IllegalArgumentException, FloatBuffer] =
    IO.effect(JFloatBuffer.allocate(capacity))
      .map(new FloatBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def float(chunk: Chunk[Float]): IO[Nothing, FloatBuffer] =
    IO.effectTotal(JFloatBuffer.wrap(chunk.toArray)).map(new FloatBuffer(_))

  def floatFromJava(javaBuffer: JFloatBuffer): FloatBuffer = new FloatBuffer(javaBuffer)

  def double(capacity: Int): IO[IllegalArgumentException, DoubleBuffer] =
    IO.effect(JDoubleBuffer.allocate(capacity))
      .map(new DoubleBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def double(chunk: Chunk[Double]): IO[Nothing, DoubleBuffer] =
    IO.effectTotal(JDoubleBuffer.wrap(chunk.toArray)).map(new DoubleBuffer(_))

  def doubleFromJava(javaBuffer: JDoubleBuffer): DoubleBuffer = new DoubleBuffer(javaBuffer)

  def int(capacity: Int): IO[IllegalArgumentException, IntBuffer] =
    IO.effect(JIntBuffer.allocate(capacity))
      .map(new IntBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def int(chunk: Chunk[Int]): IO[Nothing, IntBuffer] =
    IO.effectTotal(JIntBuffer.wrap(chunk.toArray)).map(new IntBuffer(_))

  def intFromJava(javaBuffer: JIntBuffer): IntBuffer = new IntBuffer(javaBuffer)

  def long(capacity: Int): IO[IllegalArgumentException, LongBuffer] =
    IO.effect(JLongBuffer.allocate(capacity))
      .map(new LongBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def long(chunk: Chunk[Long]): IO[Nothing, LongBuffer] =
    IO.effectTotal(JLongBuffer.wrap(chunk.toArray)).map(new LongBuffer(_))

  def longFromJava(javaBuffer: JLongBuffer): LongBuffer = new LongBuffer(javaBuffer)

  def short(capacity: Int): IO[IllegalArgumentException, ShortBuffer] =
    IO.effect(JShortBuffer.allocate(capacity))
      .map(new ShortBuffer(_))
      .refineToOrDie[IllegalArgumentException]

  def short(chunk: Chunk[Short]): IO[Nothing, ShortBuffer] =
    IO.effectTotal(JShortBuffer.wrap(chunk.toArray)).map(new ShortBuffer(_))

  def shortFromJava(javaBuffer: JShortBuffer): ShortBuffer = new ShortBuffer(javaBuffer)

}
