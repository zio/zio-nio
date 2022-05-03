package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace, UIO, ZIO}

import java.nio.{
  Buffer => JBuffer,
  ByteBuffer => JByteBuffer,
  ByteOrder,
  CharBuffer => JCharBuffer,
  DoubleBuffer => JDoubleBuffer,
  FloatBuffer => JFloatBuffer,
  IntBuffer => JIntBuffer,
  LongBuffer => JLongBuffer,
  ShortBuffer => JShortBuffer
}
import scala.reflect.ClassTag

/**
 * Mutable buffer of value elements.
 *
 * This wraps one of the Java NIO buffer classes. Most of
 * [[https://docs.oracle.com/javase/8/docs/api/java/nio/Buffer.html the Java documentation]] is applicable to this
 * class.
 *
 * Buffer instances are in no way synchronized and are typically used from a single fiber. Extract immutable `Chunk`s to
 * pass values to other fibers or via streams.
 *
 * =Construction=
 *
 * There is a concrete buffer subclass for each primitive type. There are three ways to create a buffer:
 *
 * ==Allocation==
 *
 * Simply allocates a new buffer on the heap of a certain size, for example `Buffer.byte(100)`, `Buffer.char(200)`.
 *
 * For `ByteBuffer` there is the special case of direct buffers, which can be constructed via `Buffer.byteDirect`. See
 * the Java docs for details of the advantages and disadvantages of direct byte buffers.
 *
 * ==Wrapping==
 *
 * Buffers can wrap existing Java buffers, or ZIO `Chunk`s. Care must be taken when wrapping Java buffers that the
 * wrapped buffer is not subsequently modified; these objects are typically wrapped to provide efficient
 * interoperability with Java APIs.
 *
 * A `CharBuffer` can also wrap any `java.lang.CharSequence`.
 *
 * ==Views==
 *
 * While `ByteBuffer`s are created via allocation or wrapping, the other buffer types are more commonly constructed as a
 * ''view'' over a `ByteBuffer`. Each numeric buffer class supports either big-endian or little-endian byte order as
 * required.
 *
 * View buffers are constructed via the various `asXXX` methods on the [[zio.nio.ByteBuffer]] class, for example:
 *
 * {{{
 *   val ints(implicit trace: Trace): UIO[IntBuffer] = bytes.asIntBuffer
 * }}}
 *
 * Changes to made via view buffers are reflected in the original `ByteBuffer` and vice-versa.
 *
 * =Differences from Java=
 *
 * The Java API supports "invocation chaining", which does not work with effect values. The Java example:
 *
 * {{{
 *   b.flip().position(23).limit(42)
 * }}}
 *
 * is typically written:
 *
 * {{{
 *   for {
 *     _ <- b.flip
 *     _ <- b.position(23)
 *     _ <- b.limit(42)
 *   } yield ()
 * }}}
 *
 * or in cases like this when the intermediate values aren't used:
 *
 * {{{
 *   b.flip *> b.position(23) *> b.limit(42)
 * }}}
 */
@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private[nio] () {

  protected[nio] val buffer: JBuffer

  /**
   * Returns this buffer's capacity.
   */
  final def capacity: Int = buffer.capacity

  /**
   * The byte order used for reading multiple byte values.
   *
   * Also the byte order used any view buffers created from this buffer.
   */
  def order(implicit trace: Trace): UIO[ByteOrder]

  /**
   * Returns this buffer's position.
   */
  final def position(implicit trace: Trace): UIO[Int] = ZIO.succeed(buffer.position)

  /**
   * Sets this buffer's position.
   *
   * Dies with `IllegalArgumentException` if the new position is outside the bounds.
   *
   * @param newPosition
   *   Must be >= 0 and <= the current limit.
   */
  final def position(newPosition: Int)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(buffer.position(newPosition)).unit

  /**
   * Moves this buffer's position forward or backwards by a delta.
   *
   * @param delta
   *   The number of elements to move, negative to move backwards.
   * @return
   *   The new position.
   */
  final def movePosition(delta: Int)(implicit trace: Trace): UIO[Int] =
    for {
      pos   <- position
      newPos = pos + delta
      _     <- position(newPos)
    } yield newPos

  /**
   * Returns this buffer's limit.
   */
  final def limit(implicit trace: Trace): UIO[Int] = ZIO.succeed(buffer.limit)

  /**
   * Sets this buffer's limit.
   *
   * Dies with `IllegalArgumentException` if the new limit is outside the bounds.
   *
   * @param newLimit
   *   Must be >= 0 and <= this buffer's capacity.
   */
  final def limit(newLimit: Int)(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.limit(newLimit)).unit

  /**
   * Moves this buffer's limit forward or backwards by a delta.
   *
   * @param delta
   *   The number of elements to move, negative to move backwards.
   * @return
   *   The new limit.
   */
  final def moveLimit(delta: Int)(implicit trace: Trace): UIO[Int] =
    for {
      pos   <- limit
      newPos = pos + delta
      _     <- limit(newPos)
    } yield newPos

  /**
   * Returns the number of elements between this buffer's position and its limit.
   */
  final def remaining(implicit trace: Trace): UIO[Int] = ZIO.succeed(buffer.remaining)

  /**
   * Indicates whether there are any elements between this buffer's position and its limit.
   */
  final def hasRemaining(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(buffer.hasRemaining)

  /**
   * Sets this buffer's mark to the current position.
   */
  final def mark(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.mark()).unit

  /**
   * Resets the position to the previously set mark. A mark ''must'' be set before calling this.
   *
   * Dies with `InvalidMarkException` if a mark has not previously been set.
   */
  final def reset(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.reset()).unit

  /**
   * Clears this buffer. The position is set to zero, the limit is set to the capacity, and the mark is discarded. No
   * values in the buffer are actually cleared, but this is typically used before putting new values into a buffer,
   * after all its contents have been processed.
   *
   * If the buffer's current values have not been completely processed, then the `compact` method may be more
   * appropriate.
   */
  final def clear(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.clear()).unit

  /**
   * Flips this buffer. The limit is set to the current position and then the position is set to zero. If the mark is
   * defined then it is discarded. After a sequence of channel-read or put operations, invoke this method to prepare for
   * a sequence of channel-write or relative get operations.
   *
   * This method is often used in conjunction with the `compact` method when transferring data from one place to
   * another.
   */
  final def flip(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.flip()).unit

  /**
   * Rewinds this buffer. The position is set to zero and the mark is discarded. Invoke this method before a sequence of
   * channel-write or get operations, assuming that the limit has already been set appropriately.
   */
  final def rewind(implicit trace: Trace): UIO[Unit] = ZIO.succeed(buffer.rewind()).unit

  /**
   * Indicates if this buffer is read-only.
   *
   * Calling any put methods on a read-only buffer with throw `ReadOnlyBufferException`.
   */
  final def isReadOnly: Boolean = buffer.isReadOnly

  /**
   * Indicates if this buffer is backed by an array on the heap.
   *
   * The underlying array can be used in a safe way via the `withArray` method.
   */
  final def hasArray: Boolean = buffer.hasArray

  /**
   * Indicates if this buffer was directly allocated.
   *
   * Returns true for directly allocated `ByteBuffer`s and view buffers created from them.
   */
  final def isDirect: Boolean = buffer.isDirect

  /**
   * Creates a new buffer whose content is a shared subsequence of this buffer's content. The content of the new buffer
   * will start at this buffer's current position. Changes to this buffer's content will be visible in the new buffer,
   * and vice versa; the two buffers' position, limit, and mark values will be independent.
   *
   * The new buffer's position will be zero, its capacity and its limit will be the number of bytes remaining in this
   * buffer.
   */
  def slice(implicit trace: Trace): UIO[Buffer[A]]

  /**
   * Compacts this buffer (optional operation). The bytes between the buffer's current position and its limit, if any,
   * are copied to the beginning of the buffer. That is, the byte at index `p = position()` is copied to index `0`, the
   * byte at index `p + 1` is copied to index `1`, and so forth until the byte at index `limit() - 1` is copied to index
   * `n = limit() - 1 - p`. The buffer's position is then set to `n+1` and its limit is set to its capacity. The mark,
   * if defined, is discarded.
   *
   * The buffer's position is set to the number of bytes copied, rather than to zero, so that an invocation of this
   * method can be followed immediately by an invocation of another relative put method.
   *
   * Invoke this method after writing data from a buffer in case the write was incomplete.
   *
   * Dies with `ReadOnlyBufferException` if this buffer is read-only.
   */
  def compact(implicit trace: Trace): UIO[Unit]

  /**
   * Creates a new buffer that shares this buffer's content. The content of the new buffer will be that of this buffer.
   * Changes to this buffer's content will be visible in the new buffer, and vice versa; the two buffers' position,
   * limit, and mark values will be independent.
   *
   * The new buffer's capacity, limit, position, and mark values will be identical to those of this buffer.
   */
  def duplicate(implicit trace: Trace): UIO[Buffer[A]]

  /**
   * Perform effects using this buffer's underlying array directly. Because only some buffers are backed by arrays, two
   * cases must be handled. Ideally, the same result is produced in each case, with the `hasArray` variant just being
   * more efficient.
   *
   * For the `hasArray` case, the function is provided the backing array itself and an offset within that array which
   * contains the first element of this buffer. Elements in the array before the offset are not contained in this
   * buffer.
   *
   * @param noArray
   *   The effect to perform if this buffer is not backed by an array.
   * @param hasArray
   *   The effect to perform if this buffer is backed by an array.
   */
  final def withArray[R, E, B](
    noArray: ZIO[R, E, B],
    hasArray: (Array[A], Int) => ZIO[R, E, B]
  )(implicit trace: Trace): ZIO[R, E, B] =
    if (buffer.hasArray)
      for {
        a      <- array
        offset <- ZIO.succeed(buffer.arrayOffset())
        result <- hasArray(a, offset)
      } yield result
    else
      noArray

  protected[nio] def array(implicit trace: Trace): UIO[Array[A]]

  /**
   * Relative get of a single element. Reads the element at the position and increments the position.
   *
   * Dies with `BufferUnderflowException` If there are no elements remaining.
   */
  def get(implicit trace: Trace): UIO[A]

  /**
   * Absolute get of a single element. Reads the element at the given index. The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit.
   */
  def get(i: Int)(implicit trace: Trace): UIO[A]

  /**
   * Relative get of multiple elements.
   *
   * Reads up to the specified number of elements from the current position. If fewer than `maxLength` elements are
   * remaining, then all the remaining elements are read. The position is incremented by the number of elements read.
   *
   * @param maxLength
   *   Defaults to `Int.MaxValue`, meaning all remaining elements will be read.
   */
  def getChunk(maxLength: Int = Int.MaxValue)(implicit trace: Trace): UIO[Chunk[A]]

  /**
   * Relative put of a single element. Writes the element at the position and increments the position.
   *
   * Dies with `BufferOverflowException` if there are no elements remaining. Dies with `ReadOnlyBufferException` if this
   * is a read-only buffer.
   */
  def put(element: A)(implicit trace: Trace): UIO[Unit]

  /**
   * Absolute put of a single element. Writes the element at the specified index. The position does not change.
   *
   * Dies with `IndexOutOfBoundsException` if the index is negative or not smaller than the limit. Dies with
   * `ReadOnlyBufferException` if this is a read-only buffer.
   */
  def put(index: Int, element: A)(implicit trace: Trace): UIO[Unit]

  /**
   * Tries to put an entire chunk in this buffer, possibly overflowing.
   *
   * `putChunk` is a safe public variant of this that won't overflow.
   */
  protected def putChunkAll(chunk: Chunk[A])(implicit trace: Trace): UIO[Unit]

  /**
   * Relative put of multiple elements. Writes as many elements as can fit in remaining buffer space, returning any
   * elements that did not fit.
   *
   * @return
   *   The remaining elements that could not fit in this buffer, if any.
   */
  final def putChunk(chunk: Chunk[A])(implicit trace: Trace): UIO[Chunk[A]] =
    for {
      r                         <- remaining
      (putChunk, remainderChunk) = chunk.splitAt(r)
      _                         <- this.putChunkAll(putChunk)
    } yield remainderChunk

  /**
   * Creates a read-only view of this buffer.
   */
  def asReadOnlyBuffer(implicit trace: Trace): UIO[Buffer[A]]

}

object Buffer {

  /**
   * Allocates a byte buffer backed by a new array.
   *
   * The new buffer's position will be 0, and its limit will be its capacity.
   *
   * Dies with `IllegalArgumentException` if `capacity` is negative.
   *
   * @param capacity
   *   The number of bytes to allocate.
   */
  def byte(capacity: Int)(implicit trace: Trace): UIO[ByteBuffer] =
    ZIO.succeed(byteFromJava(JByteBuffer.allocate(capacity)))

  /**
   * Creates a new array-backed buffer containing data copied from a chunk.
   *
   * The new buffer will have a capacity equal to the chunk length, its position will be 0 and its limit set to the
   * capacity.
   *
   * @param chunk
   *   The data to copy into the new buffer.
   */
  def byte(chunk: Chunk[Byte])(implicit trace: Trace): UIO[ByteBuffer] =
    ZIO.succeed(byteFromJava(JByteBuffer.wrap(chunk.toArray)))

  /**
   * Allocates a direct byte buffer.
   *
   * The new buffer's position will be 0, and its limit will be its capacity.
   *
   * Dies with `IllegalArgumentException` if `capacity` is negative.
   *
   * @param capacity
   *   The number of bytes to allocate.
   */
  def byteDirect(capacity: Int)(implicit trace: Trace): UIO[ByteBuffer] =
    ZIO.succeed(byteFromJava(JByteBuffer.allocateDirect(capacity)))

  /**
   * Wraps an existing Java `ByteBuffer`.
   *
   * This is only useful for inter-operating with Java APIs that provide Java byte buffers.
   */
  def byteFromJava(javaBuffer: JByteBuffer): ByteBuffer = new ByteBuffer(javaBuffer)

  /**
   * Allocates a char buffer backed by a new array.
   *
   * The new buffer's position will be 0, and its limit will be its capacity.
   *
   * Dies with `IllegalArgumentException` if `capacity` is negative.
   *
   * @param capacity
   *   The number of characters to allocate.
   */
  def char(capacity: Int)(implicit trace: Trace): UIO[CharBuffer] =
    ZIO.succeed(charFromJava(JCharBuffer.allocate(capacity)))

  /**
   * Creates a new array-backed buffer containing data copied from a chunk.
   *
   * The new buffer will have a capacity equal to the chunk length, its position will be 0 and its limit set to the
   * capacity.
   *
   * @param chunk
   *   The data to copy into the new buffer.
   */
  def char(chunk: Chunk[Char])(implicit trace: Trace): UIO[CharBuffer] =
    ZIO.succeed(charFromJava(JCharBuffer.wrap(chunk.toArray)))

  /**
   * Creates a read-only character buffer wrapping a character sequence.
   *
   * The new buffer's capacity will be `charSequence.length`, its position will be `start` and its limit will be `end`.
   *
   * Dies with `IndexOutOfBoundsException` if `start` or `end` are out of bounds.
   *
   * @param charSequence
   *   The characters to wrap.
   * @param start
   *   must be >= 0 and <= capacity
   * @param end
   *   must be >= start and <= capacity
   */
  def char(
    charSequence: CharSequence,
    start: Int,
    end: Int
  )(implicit trace: Trace): UIO[CharBuffer] =
    ZIO.succeed(charFromJava(JCharBuffer.wrap(charSequence, start, end)))

  /**
   * Creates a read-only character buffer wrapping a character sequence.
   *
   * The new buffer's capacity and limit will be `charSequence.length` and its position will be 0.
   *
   * @param charSequence
   *   The characters to wrap.
   */
  def char(charSequence: CharSequence)(implicit trace: Trace): UIO[CharBuffer] =
    ZIO.succeed(new CharBuffer(JCharBuffer.wrap(charSequence)))

  /**
   * Wraps an existing Java `CharBuffer`.
   *
   * This is only useful for inter-operating with Java APIs that provide Java byte buffers.
   */
  def charFromJava(javaBuffer: JCharBuffer): CharBuffer = new CharBuffer(javaBuffer)

  /**
   * Allocates a float buffer backed by a new array.
   *
   * The new buffer's position will be 0, and its limit will be its capacity.
   *
   * Dies with `IllegalArgumentException` if `capacity` is negative.
   *
   * @param capacity
   *   The number of floats to allocate.
   */
  def float(capacity: Int)(implicit trace: Trace): UIO[FloatBuffer] =
    ZIO.succeed(floatFromJava(JFloatBuffer.allocate(capacity)))

  /**
   * Creates a new array-backed buffer containing data copied from a chunk.
   *
   * The new buffer will have a capacity equal to the chunk length, its position will be 0 and its limit set to the
   * capacity.
   *
   * @param chunk
   *   The data to copy into the new buffer.
   */
  def float(chunk: Chunk[Float])(implicit trace: Trace): UIO[FloatBuffer] =
    ZIO.succeed(floatFromJava(JFloatBuffer.wrap(chunk.toArray)))

  /**
   * Wraps an existing Java `FloatBuffer`.
   *
   * This is only useful for inter-operating with Java APIs that provide Java byte buffers.
   */
  def floatFromJava(javaBuffer: JFloatBuffer): FloatBuffer = new FloatBuffer(javaBuffer)

  /**
   * Allocates an double buffer backed by a new array.
   *
   * The new buffer's position will be 0, and its limit will be its capacity.
   *
   * Dies with `IllegalArgumentException` if `capacity` is negative.
   *
   * @param capacity
   *   The number of doubles to allocate.
   */
  def double(capacity: Int)(implicit trace: Trace): UIO[DoubleBuffer] =
    ZIO.succeed(doubleFromJava(JDoubleBuffer.allocate(capacity)))

  /**
   * Creates a new array-backed buffer containing data copied from a chunk.
   *
   * The new buffer will have a capacity equal to the chunk length, its position will be 0 and its limit set to the
   * capacity.
   *
   * @param chunk
   *   The data to copy into the new buffer.
   */
  def double(chunk: Chunk[Double])(implicit trace: Trace): UIO[DoubleBuffer] =
    ZIO.succeed(doubleFromJava(JDoubleBuffer.wrap(chunk.toArray)))

  /**
   * Wraps an existing Java `DoubleBuffer`.
   *
   * This is only useful for inter-operating with Java APIs that provide Java byte buffers.
   */
  def doubleFromJava(javaBuffer: JDoubleBuffer): DoubleBuffer = new DoubleBuffer(javaBuffer)

  /**
   * Allocates an int buffer backed by a new array.
   *
   * The new buffer's position will be 0, and its limit will be its capacity.
   *
   * Dies with `IllegalArgumentException` if `capacity` is negative.
   *
   * @param capacity
   *   The number of ints to allocate.
   */
  def int(capacity: Int)(implicit trace: Trace): UIO[IntBuffer] =
    ZIO.succeed(intFromJava(JIntBuffer.allocate(capacity)))

  /**
   * Creates a new array-backed buffer containing data copied from a chunk.
   *
   * The new buffer will have a capacity equal to the chunk length, its position will be 0 and its limit set to the
   * capacity.
   *
   * @param chunk
   *   The data to copy into the new buffer.
   */
  def int(chunk: Chunk[Int])(implicit trace: Trace): UIO[IntBuffer] =
    ZIO.succeed(intFromJava(JIntBuffer.wrap(chunk.toArray)))

  /**
   * Wraps an existing Java `IntBuffer`.
   *
   * This is only useful for inter-operating with Java APIs that provide Java byte buffers.
   */
  def intFromJava(javaBuffer: JIntBuffer): IntBuffer = new IntBuffer(javaBuffer)

  /**
   * Allocates a long buffer backed by a new array.
   *
   * The new buffer's position will be 0, and its limit will be its capacity.
   *
   * Dies with `IllegalArgumentException` if `capacity` is negative.
   *
   * @param capacity
   *   The number of longs to allocate.
   */
  def long(capacity: Int)(implicit trace: Trace): UIO[LongBuffer] =
    ZIO.succeed(longFromJava(JLongBuffer.allocate(capacity)))

  /**
   * Creates a new array-backed buffer containing data copied from a chunk.
   *
   * The new buffer will have a capacity equal to the chunk length, its position will be 0 and its limit set to the
   * capacity.
   *
   * @param chunk
   *   The data to copy into the new buffer.
   */
  def long(chunk: Chunk[Long])(implicit trace: Trace): UIO[LongBuffer] =
    ZIO.succeed(longFromJava(JLongBuffer.wrap(chunk.toArray)))

  /**
   * Wraps an existing Java `LongBuffer`.
   *
   * This is only useful for inter-operating with Java APIs that provide Java byte buffers.
   */
  def longFromJava(javaBuffer: JLongBuffer): LongBuffer = new LongBuffer(javaBuffer)

  /**
   * Allocates a short buffer backed by a new array.
   *
   * The new buffer's position will be 0, and its limit will be its capacity.
   *
   * Dies with `IllegalArgumentException` if `capacity` is negative.
   *
   * @param capacity
   *   The number of shorts to allocate.
   */
  def short(capacity: Int)(implicit trace: Trace): UIO[ShortBuffer] =
    ZIO.succeed(shortFromJava(JShortBuffer.allocate(capacity)))

  /**
   * Creates a new array-backed buffer containing data copied from a chunk.
   *
   * The new buffer will have a capacity equal to the chunk length, its position will be 0 and its limit set to the
   * capacity.
   *
   * @param chunk
   *   The data to copy into the new buffer.
   */
  def short(chunk: Chunk[Short])(implicit trace: Trace): UIO[ShortBuffer] =
    ZIO.succeed(shortFromJava(JShortBuffer.wrap(chunk.toArray)))

  /**
   * Wraps an existing Java `ShortBuffer`.
   *
   * This is only useful for inter-operating with Java APIs that provide Java byte buffers.
   */
  def shortFromJava(javaBuffer: JShortBuffer): ShortBuffer = new ShortBuffer(javaBuffer)

}
