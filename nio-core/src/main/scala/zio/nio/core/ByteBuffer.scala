package zio.nio.core

import zio.{ Chunk, IO, UIO, ZIO }
import java.nio.{ BufferUnderflowException, ByteOrder, ReadOnlyBufferException, ByteBuffer => JByteBuffer }

class ByteBuffer protected[nio] (protected[nio] val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {

  final override protected[nio] def array: IO[UnsupportedOperationException, Array[Byte]] =
    IO.effect(byteBuffer.array()).refineToOrDie[UnsupportedOperationException]

  final def order: ByteOrder = byteBuffer.order()

  def order(o: ByteOrder): UIO[Unit] =
    UIO.effectTotal(byteBuffer.order(o)).unit

  final override def slice: IO[Nothing, ByteBuffer] =
    IO.effectTotal(byteBuffer.slice()).map(new ByteBuffer(_))

  final override def compact: IO[ReadOnlyBufferException, Unit] =
    IO.effect(byteBuffer.compact()).unit.refineToOrDie[ReadOnlyBufferException]

  final override def duplicate: IO[Nothing, ByteBuffer] =
    IO.effectTotal(new ByteBuffer(byteBuffer.duplicate()))

  def withJavaBuffer[R, E, A](f: JByteBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(byteBuffer)

  final override def get: IO[BufferUnderflowException, Byte] =
    IO.effect(byteBuffer.get()).refineToOrDie[BufferUnderflowException]

  final override def get(i: Int): IO[IndexOutOfBoundsException, Byte] =
    IO.effect(byteBuffer.get(i)).refineToOrDie[IndexOutOfBoundsException]

  final override def getChunk(maxLength: Int = Int.MaxValue): IO[BufferUnderflowException, Chunk[Byte]] =
    IO.effect {
      val array = Array.ofDim[Byte](math.min(maxLength, byteBuffer.remaining()))
      byteBuffer.get(array)
      Chunk.fromArray(array)
    }.refineToOrDie[BufferUnderflowException]

  final override def put(element: Byte): IO[Exception, Unit] =
    IO.effect(byteBuffer.put(element)).unit.refineToOrDie[Exception]

  final override def put(index: Int, element: Byte): IO[Exception, Unit] =
    IO.effect(byteBuffer.put(index, element)).unit.refineToOrDie[Exception]

  def putByteBuffer(source: ByteBuffer): IO[Exception, Unit] =
    IO.effect(byteBuffer.put(source.byteBuffer)).unit.refineToOrDie[Exception]

  final override def putChunk(chunk: Chunk[Byte]): IO[Exception, Unit] =
    IO.effect {
      val array = chunk.toArray
      byteBuffer.put(array)
    }.unit
      .refineToOrDie[Exception]

  final override def asReadOnlyBuffer: IO[Nothing, ByteBuffer] =
    IO.effectTotal(byteBuffer.asReadOnlyBuffer()).map(new ByteBuffer(_))

  final def asCharBuffer: IO[Nothing, CharBuffer] =
    IO.effectTotal(new CharBuffer(byteBuffer.asCharBuffer()))

  final def asDoubleBuffer: IO[Nothing, DoubleBuffer] =
    IO.effectTotal(new DoubleBuffer(byteBuffer.asDoubleBuffer()))

  final def asFloatBuffer: IO[Nothing, FloatBuffer] =
    IO.effectTotal(new FloatBuffer(byteBuffer.asFloatBuffer()))

  final def asIntBuffer: IO[Nothing, IntBuffer] =
    IO.effectTotal(new IntBuffer(byteBuffer.asIntBuffer()))

  final def asLongBuffer: IO[Nothing, LongBuffer] =
    IO.effectTotal(new LongBuffer(byteBuffer.asLongBuffer()))

  final def asShortBuffer: IO[Nothing, ShortBuffer] =
    IO.effectTotal(new ShortBuffer(byteBuffer.asShortBuffer()))

  final def putChar(value: Char): IO[Exception, Unit] =
    IO.effect(byteBuffer.putChar(value)).unit.refineToOrDie[Exception]

  final def putChar(index: Int, value: Char): IO[Exception, Unit] =
    IO.effect(byteBuffer.putChar(index, value)).unit.refineToOrDie[Exception]

  final def putDouble(value: Double): IO[Exception, Unit] =
    IO.effect(byteBuffer.putDouble(value)).unit.refineToOrDie[Exception]

  final def putDouble(index: Int, value: Double): IO[Exception, Unit] =
    IO.effect(byteBuffer.putDouble(index, value)).unit.refineToOrDie[Exception]

  final def putFloat(value: Float): IO[Exception, Unit] =
    IO.effect(byteBuffer.putFloat(value)).unit.refineToOrDie[Exception]

  final def putFloat(index: Int, value: Float): IO[Exception, Unit] =
    IO.effect(byteBuffer.putFloat(index, value)).unit.refineToOrDie[Exception]

  final def putInt(value: Int): IO[Exception, Unit] =
    IO.effect(byteBuffer.putInt(value)).unit.refineToOrDie[Exception]

  final def putInt(index: Int, value: Int): IO[Exception, Unit] =
    IO.effect(byteBuffer.putInt(index, value)).unit.refineToOrDie[Exception]

  final def putLong(value: Long): IO[Exception, Unit] =
    IO.effect(byteBuffer.putLong(value)).unit.refineToOrDie[Exception]

  final def putLong(index: Int, value: Long): IO[Exception, Unit] =
    IO.effect(byteBuffer.putLong(index, value)).unit.refineToOrDie[Exception]

  final def putShort(value: Short): IO[Exception, Unit] =
    IO.effect(byteBuffer.putShort(value)).unit.refineToOrDie[Exception]

  final def putShort(index: Int, value: Short): IO[Exception, Unit] =
    IO.effect(byteBuffer.putShort(index, value)).unit.refineToOrDie[Exception]

  final def getChar: IO[BufferUnderflowException, Char] =
    IO.effect(byteBuffer.getChar()).refineToOrDie[BufferUnderflowException]

  final def getChar(index: Int): IO[IndexOutOfBoundsException, Char] =
    IO.effect(byteBuffer.getChar(index)).refineToOrDie[IndexOutOfBoundsException]

  final def getDouble: IO[BufferUnderflowException, Double] =
    IO.effect(byteBuffer.getDouble()).refineToOrDie[BufferUnderflowException]

  final def getDouble(index: Int): IO[IndexOutOfBoundsException, Double] =
    IO.effect(byteBuffer.getDouble(index)).refineToOrDie[IndexOutOfBoundsException]

  final def getFloat: IO[BufferUnderflowException, Float] =
    IO.effect(byteBuffer.getFloat()).refineToOrDie[BufferUnderflowException]

  final def getFloat(index: Int): IO[IndexOutOfBoundsException, Float] =
    IO.effect(byteBuffer.getFloat(index)).refineToOrDie[IndexOutOfBoundsException]

  final def getInt: IO[BufferUnderflowException, Int] =
    IO.effect(byteBuffer.getInt()).refineToOrDie[BufferUnderflowException]

  final def getInt(index: Int): IO[IndexOutOfBoundsException, Int] =
    IO.effect(byteBuffer.getInt(index)).refineToOrDie[IndexOutOfBoundsException]

  final def getLong: IO[BufferUnderflowException, Long] =
    IO.effect(byteBuffer.getLong()).refineToOrDie[BufferUnderflowException]

  final def getLong(index: Int): IO[IndexOutOfBoundsException, Long] =
    IO.effect(byteBuffer.getLong(index)).refineToOrDie[IndexOutOfBoundsException]

  final def getShort: IO[BufferUnderflowException, Short] =
    IO.effect(byteBuffer.getShort()).refineToOrDie[BufferUnderflowException]

  final def getShort(index: Int): IO[IndexOutOfBoundsException, Short] =
    IO.effect(byteBuffer.getShort(index)).refineToOrDie[IndexOutOfBoundsException]
}
