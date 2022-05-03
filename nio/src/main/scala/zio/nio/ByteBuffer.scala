package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace, UIO, ZIO}

import java.nio.{ByteBuffer => JByteBuffer, ByteOrder}

/**
 * A mutable buffer of bytes.
 */
class ByteBuffer protected[nio] (protected[nio] val buffer: JByteBuffer) extends Buffer[Byte] {

  final override protected[nio] def array(implicit trace: Trace): UIO[Array[Byte]] = ZIO.succeed(buffer.array())

  final def order(implicit trace: Trace): UIO[ByteOrder] = ZIO.succeed(buffer.order())

  /**
   * Changes the byte order used by this buffer for multi-byte reads and view buffers.
   */
  def order(o: ByteOrder)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.order(o)).unit

  final override def slice(implicit trace: Trace): UIO[ByteBuffer] = ZIO.succeed(new ByteBuffer(buffer.slice()))

  final override def compact(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.compact()).unit

  final override def duplicate(implicit trace: Trace): UIO[ByteBuffer] =
    ZIO.succeed(new ByteBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java byte buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java byte buffer to be provided.
   *
   * @return
   *   The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JByteBuffer => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] = f(buffer)

  final override def get(implicit trace: Trace): UIO[Byte] = ZIO.succeed(buffer.get())

  final override def get(i: Int)(implicit trace: Trace): UIO[Byte] = ZIO.succeed(buffer.get(i))

  /**
   * Reads bytes from the current position and returns them in a `Chunk`.
   *
   * @param maxLength
   *   Defaults to `Int.MaxValue`, meaning all remaining elements will be read.
   */
  final override def getChunk(maxLength: Int = Int.MaxValue)(implicit trace: Trace): UIO[Chunk[Byte]] =
    ZIO.succeed {
      val array = Array.ofDim[Byte](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  final override def put(element: Byte)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.put(element)).unit

  final override def put(index: Int, element: Byte)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.put(index, element)).unit

  def putByteBuffer(source: ByteBuffer)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.put(source.buffer)).unit

  final override protected def putChunkAll(chunk: Chunk[Byte])(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  final override def asReadOnlyBuffer(implicit trace: Trace): UIO[ByteBuffer] =
    ZIO.succeed(new ByteBuffer(buffer.asReadOnlyBuffer()))

  final def asCharBuffer(implicit trace: Trace): UIO[CharBuffer] =
    ZIO.succeed(new CharBuffer(buffer.asCharBuffer()))

  final def asDoubleBuffer(implicit trace: Trace): UIO[DoubleBuffer] =
    ZIO.succeed(new DoubleBuffer(buffer.asDoubleBuffer()))

  final def asFloatBuffer(implicit trace: Trace): UIO[FloatBuffer] =
    ZIO.succeed(new FloatBuffer(buffer.asFloatBuffer()))

  final def asIntBuffer(implicit trace: Trace): UIO[IntBuffer] =
    ZIO.succeed(new IntBuffer(buffer.asIntBuffer()))

  final def asLongBuffer(implicit trace: Trace): UIO[LongBuffer] =
    ZIO.succeed(new LongBuffer(buffer.asLongBuffer()))

  final def asShortBuffer(implicit trace: Trace): UIO[ShortBuffer] =
    ZIO.succeed(new ShortBuffer(buffer.asShortBuffer()))

  /**
   * Relative put of a single character. Writes the character at the position using the current byte order and
   * increments the position by 2.
   *
   * Dies with `BufferOverflowException` if there are fewer than 2 bytes remaining. Dies with `ReadOnlyBufferException`
   * if this is a read-only buffer.
   */
  final def putChar(value: Char)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.putChar(value)).unit

  /**
   * Absolute put of a single character. Writes the character at the specified index using the current byte order. The
   * position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-1. Dies with
   * `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putChar(index: Int, value: Char)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.putChar(index, value)).unit

  /**
   * Relative put of a single double. Writes the double at the position using the current byte order and increments the
   * position by 8.
   *
   * Dies with `BufferOverflowException` if there are fewer than 8 bytes remaining. Dies with `ReadOnlyBufferException`
   * if this is a read-only buffer.
   */
  final def putDouble(value: Double)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.putDouble(value)).unit

  /**
   * Absolute put of a single double. Writes the double at the specified index using the current byte order. The
   * position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-7. Dies with
   * `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putDouble(index: Int, value: Double)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.putDouble(index, value)).unit

  /**
   * Relative put of a single float. Writes the float at the position using the current byte order and increments the
   * position by 4.
   *
   * Dies with `BufferOverflowException` if there are fewer than 4 bytes remaining. Dies with `ReadOnlyBufferException`
   * if this is a read-only buffer.
   */
  final def putFloat(value: Float)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.putFloat(value)).unit

  /**
   * Absolute put of a single float. Writes the float at the specified index using the current byte order. The position
   * does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-3. Dies with
   * `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putFloat(index: Int, value: Float)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.putFloat(index, value)).unit

  /**
   * Relative put of a single int. Writes the int at the position using the current byte order and increments the
   * position by 4.
   *
   * Dies with `BufferOverflowException` if there are fewer than 4 bytes remaining. Dies with `ReadOnlyBufferException`
   * if this is a read-only buffer.
   */
  final def putInt(value: Int)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.putInt(value)).unit

  /**
   * Absolute put of a single int. Writes the int at the specified index using the current byte order. The position does
   * not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-3. Dies with
   * `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putInt(index: Int, value: Int)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.putInt(index, value)).unit

  /**
   * Relative put of a single long. Writes the long at the position using the current byte order and increments the
   * position by 8.
   *
   * Dies with `BufferOverflowException` if there are fewer than 8 bytes remaining. Dies with `ReadOnlyBufferException`
   * if this is a read-only buffer.
   */
  final def putLong(value: Long)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.putLong(value)).unit

  /**
   * Absolute put of a single long. Writes the long at the specified index using the current byte order. The position
   * does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-7. Dies with
   * `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putLong(index: Int, value: Long)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.putLong(index, value)).unit

  /**
   * Relative put of a single short. Writes the short at the position using the current byte order and increments the
   * position by 2.
   *
   * Dies with `BufferOverflowException` if there are fewer than 2 bytes remaining. Dies with `ReadOnlyBufferException`
   * if this is a read-only buffer.
   */
  final def putShort(value: Short)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.putShort(value)).unit

  /**
   * Absolute put of a single short. Writes the short at the specified index using the current byte order. The position
   * does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-1. Dies with
   * `ReadOnlyBufferException` if this is a read-only buffer.
   */
  final def putShort(index: Int, value: Short)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.putShort(index, value)).unit

  /**
   * Relative get of a single character. Reads the character at the position using the current byte order and increments
   * the position by 2.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 2 bytes remaining.
   */
  final def getChar(implicit trace: Trace): UIO[Char] = ZIO.succeed(buffer.getChar())

  /**
   * Absolute get of a single character. Reads the character at the given index using the current byte order. The
   * position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-1.
   */
  final def getChar(index: Int)(implicit trace: Trace): UIO[Char] = ZIO.succeed(buffer.getChar(index))

  /**
   * Relative get of a single double. Reads the double at the position using the current byte order and increments the
   * position by 8.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 8 bytes remaining.
   */
  final def getDouble(implicit trace: Trace): UIO[Double] = ZIO.succeed(buffer.getDouble())

  /**
   * Absolute get of a single double. Reads the double at the given index using the current byte order. The position
   * does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-7.
   */
  final def getDouble(index: Int)(implicit trace: Trace): UIO[Double] = ZIO.succeed(buffer.getDouble(index))

  /**
   * Relative get of a single float. Reads the float at the position using the current byte order and increments the
   * position by 4.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 4 bytes remaining.
   */
  final def getFloat(implicit trace: Trace): UIO[Float] = ZIO.succeed(buffer.getFloat())

  /**
   * Absolute get of a single float. Reads the float at the given index using the current byte order. The position does
   * not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-3.
   */
  final def getFloat(index: Int)(implicit trace: Trace): UIO[Float] = ZIO.succeed(buffer.getFloat(index))

  /**
   * Relative get of a single int. Reads the int at the position using the current byte order and increments the
   * position by 4.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 4 bytes remaining.
   */
  final def getInt(implicit trace: Trace): UIO[Int] = ZIO.succeed(buffer.getInt())

  /**
   * Absolute get of a single int. Reads the int at the given index using the current byte order. The position does not
   * change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-3.
   */
  final def getInt(index: Int)(implicit trace: Trace): UIO[Int] = ZIO.succeed(buffer.getInt(index))

  /**
   * Relative get of a single long. Reads the long at the position using the current byte order and increments the
   * position by 8.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 8 bytes remaining.
   */
  final def getLong(implicit trace: Trace): UIO[Long] = ZIO.succeed(buffer.getLong())

  /**
   * Absolute get of a single long. Reads the long at the given index using the current byte order. The position does
   * not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-7.
   */
  final def getLong(index: Int)(implicit trace: Trace): UIO[Long] = ZIO.succeed(buffer.getLong(index))

  /**
   * Relative get of a single short. Reads the short at the position using the current byte order and increments the
   * position by 2.
   *
   * Dies with `BufferUnderflowException` If there are fewer than 2 bytes remaining.
   */
  final def getShort(implicit trace: Trace): UIO[Short] = ZIO.succeed(buffer.getShort())

  /**
   * Absolute get of a single short. Reads the short at the given index using the current byte order. The position does
   * not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit-1.
   */
  final def getShort(index: Int)(implicit trace: Trace): UIO[Short] = ZIO.succeed(buffer.getShort(index))

}
