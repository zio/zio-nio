package zio.nio

import java.nio.{ ByteOrder, IntBuffer => JIntBuffer }

import zio.{ Chunk, UIO, ZIO }

/**
 * A mutable buffer of ints.
 */
final class IntBuffer(protected[nio] val buffer: JIntBuffer) extends Buffer[Int] {

  override protected[nio] def array: UIO[Array[Int]] = UIO.effectTotal(buffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(buffer.order)

  override def slice: UIO[IntBuffer] = UIO.effectTotal(new IntBuffer(buffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(buffer.compact()).unit

  override def duplicate: UIO[IntBuffer] = UIO.effectTotal(new IntBuffer(buffer.duplicate()))

  /**
   * Provides the underlying Java int buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java int buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JIntBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(buffer)

  override def get: UIO[Int] = UIO.effectTotal(buffer.get())

  override def get(i: Int): UIO[Int] = UIO.effectTotal(buffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Int]] =
    UIO.effectTotal {
      val array = Array.ofDim[Int](math.min(maxLength, buffer.remaining()))
      buffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Int): UIO[Unit] = UIO.effectTotal(buffer.put(element)).unit

  override def put(index: Int, element: Int): UIO[Unit] = UIO.effectTotal(buffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Int]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      buffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[IntBuffer] = UIO.effectTotal(new IntBuffer(buffer.asReadOnlyBuffer()))

}
