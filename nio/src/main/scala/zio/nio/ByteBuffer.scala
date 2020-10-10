package zio.nio

import zio.{ Chunk, UIO, ZIO }
import java.nio.{ ByteOrder, ByteBuffer => JByteBuffer }

/**
 * A mutable buffer of bytes.
 */
class ByteBuffer protected[nio] (protected[nio] val buffer: JByteBuffer) extends Buffer[Byte] {

  final override protected[nio] def array: UIO[Array[Byte]] = UIO.effectTotal(buffer.array())

  final def order: UIO[ByteOrder] = UIO.effectTotal(buffer.order())

  /**
   * Changes the byte order used by this buffer for multi-byte reads and view buffers.
   */
  def order(o: ByteOrder): UIO[Unit] = UIO.effectTotal(buffer.order(o)).unit

  final override def slice: UIO[ByteBuffer] = UIO.effectTotal(new ByteBuffer(buffer.slice()))

  final override def compact: UIO[Unit] = UIO.effectTotal(buffer.compact()).unit

  final override def duplicate: UIO[ByteBuffer] = UIO.effectTotal(new ByteBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java byte buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java byte buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JByteBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(buffer)

  final override def get: UIO[Byte] = UIO.effectTotal(buffer.get())

  final override def get(i: Int): UIO[Byte] = UIO.effectTotal(buffer.get(i))

  /**
   * Reads bytes from the current position and returns them in a `Chunk`.
   *
   * @param maxLength Defaults to `Int.MaxValue`, meaning all remaining
   *                  elements will be read.
   */
  final override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Byte]] =
    UIO.effectTotal {
      val array = Array.ofDim[Byte](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  final override def put(element: Byte): UIO[Unit] = UIO.effectTotal(buffer.put(element)).unit

  final override def put(index: Int, element: Byte): UIO[Unit] = UIO.effectTotal(buffer.put(index, element)).unit

  def putByteBuffer(source: ByteBuffer): UIO[Unit] = UIO.effectTotal(buffer.put(source.buffer)).unit

  final override protected def putChunkAll(chunk: Chunk[Byte]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  final override def asReadOnlyBuffer: UIO[ByteBuffer] = UIO.effectTotal(new ByteBuffer(buffer.asReadOnlyBuffer()))

  final def asCharBuffer: UIO[CharBuffer] = UIO.effectTotal(new CharBuffer(buffer.asCharBuffer()))

  final def asDoubleBuffer: UIO[DoubleBuffer] = UIO.effectTotal(new DoubleBuffer(buffer.asDoubleBuffer()))

  final def asFloatBuffer: UIO[FloatBuffer] = UIO.effectTotal(new FloatBuffer(buffer.asFloatBuffer()))

  final def asIntBuffer: UIO[IntBuffer] = UIO.effectTotal(new IntBuffer(buffer.asIntBuffer()))

  final def asLongBuffer: UIO[LongBuffer] = UIO.effectTotal(new LongBuffer(buffer.asLongBuffer()))

  final def asShortBuffer: UIO[ShortBuffer] = UIO.effectTotal(new ShortBuffer(buffer.asShortBuffer()))

  /**
   * Relative put of a single character.
   * Writes the character at the position using the current byte order
   * and increments the position by 2.
   *
   * Dies with `BufferOverflowException` if there are fewer than 2 bytes remaining.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putChar(value: Char): UIO[Unit] = UIO.effectTotal(buffer.putChar(value)).unit

  /**
   * Absolute put of a single character.
   * Writes the character at the specified index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-1.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putChar(index: Int, value: Char): UIO[Unit] = UIO.effectTotal(buffer.putChar(index, value)).unit

  /**
   * Relative put of a single double.
   * Writes the double at the position using the current byte order
   * and increments the position by 8.
   *
   * Dies with `BufferOverflowException` if there are fewer than 8 bytes remaining.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putDouble(value: Double): UIO[Unit] = UIO.effectTotal(buffer.putDouble(value)).unit

  /**
   * Absolute put of a single double.
   * Writes the double at the specified index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-7.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putDouble(index: Int, value: Double): UIO[Unit] = UIO.effectTotal(buffer.putDouble(index, value)).unit

  /**
   * Relative put of a single float.
   * Writes the float at the position using the current byte order
   * and increments the position by 4.
   *
   * Dies with `BufferOverflowException` if there are fewer than 4 bytes remaining.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putFloat(value: Float): UIO[Unit] = UIO.effectTotal(buffer.putFloat(value)).unit

  /**
   * Absolute put of a single float.
   * Writes the float at the specified index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-3.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putFloat(index: Int, value: Float): UIO[Unit] = UIO.effectTotal(buffer.putFloat(index, value)).unit

  /**
   * Relative put of a single int.
   * Writes the int at the position using the current byte order
   * and increments the position by 4.
   *
   * Dies with `BufferOverflowException` if there are fewer than 4 bytes remaining.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putInt(value: Int): UIO[Unit] = UIO.effectTotal(buffer.putInt(value)).unit

  /**
   * Absolute put of a single int.
   * Writes the int at the specified index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-3.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putInt(index: Int, value: Int): UIO[Unit] = UIO.effectTotal(buffer.putInt(index, value)).unit

  /**
   * Relative put of a single long.
   * Writes the long at the position using the current byte order
   * and increments the position by 8.
   *
   * Dies with `BufferOverflowException` if there are fewer than 8 bytes remaining.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putLong(value: Long): UIO[Unit] = UIO.effectTotal(buffer.putLong(value)).unit

  /**
   * Absolute put of a single long.
   * Writes the long at the specified index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-7.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putLong(index: Int, value: Long): UIO[Unit] = UIO.effectTotal(buffer.putLong(index, value)).unit

  /**
   * Relative put of a single short.
   * Writes the short at the position using the current byte order
   * and increments the position by 2.
   *
   * Dies with `BufferOverflowException` if there are fewer than 2 bytes remaining.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putShort(value: Short): UIO[Unit] = UIO.effectTotal(buffer.putShort(value)).unit

  /**
   * Absolute put of a single short.
   * Writes the short at the specified index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-1.
   * Dies with `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putShort(index: Int, value: Short): UIO[Unit] = UIO.effectTotal(buffer.putShort(index, value)).unit

  /**
   * Relative get of a single character.
   * Reads the character at the position using the current byte order
   * and increments the position by 2.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 2 bytes remaining.
   */
  final def getChar: UIO[Char] = UIO.effectTotal(buffer.getChar())

  /**
   * Absolute get of a single character.
   * Reads the character at the given index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-1.
   */
  final def getChar(index: Int): UIO[Char] = UIO.effectTotal(buffer.getChar(index))

  /**
   * Relative get of a single double.
   * Reads the double at the position using the current byte order
   * and increments the position by 8.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 8 bytes remaining.
   */
  final def getDouble: UIO[Double] = UIO.effectTotal(buffer.getDouble())

  /**
   * Absolute get of a single double.
   * Reads the double at the given index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-7.
   */
  final def getDouble(index: Int): UIO[Double] = UIO.effectTotal(buffer.getDouble(index))

  /**
   * Relative get of a single float.
   * Reads the float at the position using the current byte order
   * and increments the position by 4.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 4 bytes remaining.
   */
  final def getFloat: UIO[Float] = UIO.effectTotal(buffer.getFloat())

  /**
   * Absolute get of a single float.
   * Reads the float at the given index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-3.
   */
  final def getFloat(index: Int): UIO[Float] = UIO.effectTotal(buffer.getFloat(index))

  /**
   * Relative get of a single int.
   * Reads the int at the position using the current byte order
   * and increments the position by 4.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 4 bytes remaining.
   */
  final def getInt: UIO[Int] = UIO.effectTotal(buffer.getInt())

  /**
   * Absolute get of a single int.
   * Reads the int at the given index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-3.
   */
  final def getInt(index: Int): UIO[Int] = UIO.effectTotal(buffer.getInt(index))

  /**
   * Relative get of a single long.
   * Reads the long at the position using the current byte order
   * and increments the position by 8.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 8 bytes remaining.
   */
  final def getLong: UIO[Long] = UIO.effectTotal(buffer.getLong())

  /**
   * Absolute get of a single long.
   * Reads the long at the given index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-7.
   */
  final def getLong(index: Int): UIO[Long] = UIO.effectTotal(buffer.getLong(index))

  /**
   * Relative get of a single short.
   * Reads the short at the position using the current byte order
   * and increments the position by 2.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 2 bytes remaining.
   */
  final def getShort: UIO[Short] = UIO.effectTotal(buffer.getShort())

  /**
   * Absolute get of a single short.
   * Reads the short at the given index using the current byte order.
   * The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-1.
   */
  final def getShort(index: Int): UIO[Short] = UIO.effectTotal(buffer.getShort(index))

}
