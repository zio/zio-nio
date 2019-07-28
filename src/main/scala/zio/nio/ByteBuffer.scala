package zio.nio

import zio.{ Chunk, IO, ZIO }
import java.nio.{
  BufferUnderflowException,
  ByteOrder,
  ReadOnlyBufferException,
  ByteBuffer => JByteBuffer
}

final class ByteBuffer(byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {

  override protected[nio] def array: IO[UnsupportedOperationException, Array[Byte]] =
    IO.effect(byteBuffer.array()).refineToOrDie[UnsupportedOperationException]

  def order: ByteOrder = byteBuffer.order()

  override def slice: IO[Nothing, ByteBuffer] =
    IO.effectTotal(byteBuffer.slice()).map(new ByteBuffer(_))

  override def compact: IO[ReadOnlyBufferException, Unit] =
    IO.effect(byteBuffer.compact()).unit.refineToOrDie[ReadOnlyBufferException]

  override def duplicate: IO[Nothing, ByteBuffer] =
    IO.effectTotal(new ByteBuffer(byteBuffer.duplicate()))

  def withJavaBuffer[R, E, A](f: JByteBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(byteBuffer)

  override def get: IO[BufferUnderflowException, Byte] =
    IO.effect(byteBuffer.get()).refineToOrDie[BufferUnderflowException]

  override def get(i: Int): IO[IndexOutOfBoundsException, Byte] =
    IO.effect(byteBuffer.get(i)).refineToOrDie[IndexOutOfBoundsException]

  override def getChunk(maxLength: Int = Int.MaxValue): IO[BufferUnderflowException, Chunk[Byte]] =
    IO.effect {
        val array = Array.ofDim[Byte](math.min(maxLength, byteBuffer.remaining()))
        byteBuffer.get(array)
        Chunk.fromArray(array)
      }
      .refineToOrDie[BufferUnderflowException]

  override def put(element: Byte): IO[Exception, Unit] =
    IO.effect(byteBuffer.put(element)).unit.refineToOrDie[Exception]

  override def put(index: Int, element: Byte): IO[Exception, Unit] =
    IO.effect(byteBuffer.put(index, element)).unit.refineToOrDie[Exception]

  override def putChunk(chunk: Chunk[Byte]): IO[Exception, Unit] =
    IO.effect {
        val array = chunk.toArray
        byteBuffer.put(array)
      }
      .unit
      .refineToOrDie[Exception]

  override def asReadOnlyBuffer: IO[Nothing, ByteBuffer] =
    IO.effectTotal(byteBuffer.asReadOnlyBuffer()).map(new ByteBuffer(_))

  def asCharBuffer: IO[Nothing, CharBuffer] =
    IO.effectTotal(new CharBuffer(byteBuffer.asCharBuffer()))

  def asDoubleBuffer: IO[Nothing, DoubleBuffer] =
    IO.effectTotal(new DoubleBuffer(byteBuffer.asDoubleBuffer()))

  def asFloatBuffer: IO[Nothing, FloatBuffer] =
    IO.effectTotal(new FloatBuffer(byteBuffer.asFloatBuffer()))

  def asIntBuffer: IO[Nothing, IntBuffer] =
    IO.effectTotal(new IntBuffer(byteBuffer.asIntBuffer()))

  def asLongBuffer: IO[Nothing, LongBuffer] =
    IO.effectTotal(new LongBuffer(byteBuffer.asLongBuffer()))

  def asShortBuffer: IO[Nothing, ShortBuffer] =
    IO.effectTotal(new ShortBuffer(byteBuffer.asShortBuffer()))

  def putChar(value: Char): IO[Exception, Unit] =
    IO.effect(byteBuffer.putChar(value)).unit.refineToOrDie[Exception]

  def putChar(index: Int, value: Char): IO[Exception, Unit] =
    IO.effect(byteBuffer.putChar(index, value)).unit.refineToOrDie[Exception]

  def putDouble(value: Double): IO[Exception, Unit] =
    IO.effect(byteBuffer.putDouble(value)).unit.refineToOrDie[Exception]

  def putDouble(index: Int, value: Double): IO[Exception, Unit] =
    IO.effect(byteBuffer.putDouble(index, value)).unit.refineToOrDie[Exception]

  def putFloat(value: Float): IO[Exception, Unit] =
    IO.effect(byteBuffer.putFloat(value)).unit.refineToOrDie[Exception]

  def putFloat(index: Int, value: Float): IO[Exception, Unit] =
    IO.effect(byteBuffer.putFloat(index, value)).unit.refineToOrDie[Exception]

  def putInt(value: Int): IO[Exception, Unit] =
    IO.effect(byteBuffer.putInt(value)).unit.refineToOrDie[Exception]

  def putInt(index: Int, value: Int): IO[Exception, Unit] =
    IO.effect(byteBuffer.putInt(index, value)).unit.refineToOrDie[Exception]

  def putLong(value: Long): IO[Exception, Unit] =
    IO.effect(byteBuffer.putLong(value)).unit.refineToOrDie[Exception]

  def putLong(index: Int, value: Long): IO[Exception, Unit] =
    IO.effect(byteBuffer.putLong(index, value)).unit.refineToOrDie[Exception]

  def putShort(value: Short): IO[Exception, Unit] =
    IO.effect(byteBuffer.putShort(value)).unit.refineToOrDie[Exception]

  def putShort(index: Int, value: Short): IO[Exception, Unit] =
    IO.effect(byteBuffer.putShort(index, value)).unit.refineToOrDie[Exception]

  def getChar: IO[BufferUnderflowException, Char] =
    IO.effect(byteBuffer.getChar()).refineToOrDie[BufferUnderflowException]

  def getChar(index: Int): IO[IndexOutOfBoundsException, Char] =
    IO.effect(byteBuffer.getChar(index)).refineToOrDie[IndexOutOfBoundsException]

  def getDouble: IO[BufferUnderflowException, Double] =
    IO.effect(byteBuffer.getDouble()).refineToOrDie[BufferUnderflowException]

  def getDouble(index: Int): IO[IndexOutOfBoundsException, Double] =
    IO.effect(byteBuffer.getDouble(index)).refineToOrDie[IndexOutOfBoundsException]

  def getFloat: IO[BufferUnderflowException, Float] =
    IO.effect(byteBuffer.getFloat()).refineToOrDie[BufferUnderflowException]

  def getFloat(index: Int): IO[IndexOutOfBoundsException, Float] =
    IO.effect(byteBuffer.getFloat(index)).refineToOrDie[IndexOutOfBoundsException]

  def getInt: IO[BufferUnderflowException, Int] =
    IO.effect(byteBuffer.getInt()).refineToOrDie[BufferUnderflowException]

  def getInt(index: Int): IO[IndexOutOfBoundsException, Int] =
    IO.effect(byteBuffer.getInt(index)).refineToOrDie[IndexOutOfBoundsException]

  def getLong: IO[BufferUnderflowException, Long] =
    IO.effect(byteBuffer.getLong()).refineToOrDie[BufferUnderflowException]

  def getLong(index: Int): IO[IndexOutOfBoundsException, Long] =
    IO.effect(byteBuffer.getLong(index)).refineToOrDie[IndexOutOfBoundsException]

  def getShort: IO[BufferUnderflowException, Short] =
    IO.effect(byteBuffer.getShort()).refineToOrDie[BufferUnderflowException]

  def getShort(index: Int): IO[IndexOutOfBoundsException, Short] =
    IO.effect(byteBuffer.getShort(index)).refineToOrDie[IndexOutOfBoundsException]
}
