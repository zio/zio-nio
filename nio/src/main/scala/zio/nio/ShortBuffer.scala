package zio.nio

import zio.{ Chunk, UIO, ZIO }

import java.nio.{ ByteOrder, ShortBuffer => JShortBuffer }

/**
 * A mutable buffer of shorts.
 */
final class ShortBuffer(protected[nio] val buffer: JShortBuffer) extends Buffer[Short] {

  override protected[nio] def array: UIO[Array[Short]] = UIO.effectTotal(buffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(buffer.order())

  override def slice: UIO[ShortBuffer] = UIO.effectTotal(new ShortBuffer(buffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(buffer.compact()).unit

  override def duplicate: UIO[ShortBuffer] = UIO.effectTotal(new ShortBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java short buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java short buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JShortBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(buffer)

  override def get: UIO[Short] = UIO.effectTotal(buffer.get())

  override def get(i: Int): UIO[Short] = UIO.effectTotal(buffer.get(i))

  override def getChunk(maxLength: Int): UIO[Chunk[Short]] =
    UIO.effectTotal {
      val array = Array.ofDim[Short](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Short): UIO[Unit] = UIO.effectTotal(buffer.put(element)).unit

  override def put(index: Int, element: Short): UIO[Unit] = UIO.effectTotal(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Short]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[ShortBuffer] = UIO.effectTotal(new ShortBuffer(buffer.asReadOnlyBuffer()))

}
