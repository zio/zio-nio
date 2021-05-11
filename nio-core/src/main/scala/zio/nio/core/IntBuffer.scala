package zio.nio.core

import zio.{ Chunk, UIO, ZIO }

import java.nio.{ ByteOrder, IntBuffer => JIntBuffer }

/**
 * A mutable buffer of ints.
 */
final class IntBuffer(intBuffer: JIntBuffer) extends Buffer[Int](intBuffer) {

  override protected[nio] def array: UIO[Array[Int]] = UIO.effectTotal(intBuffer.array())

  override def order: UIO[ByteOrder] = UIO.effectTotal(intBuffer.order)

  override def slice: UIO[IntBuffer] = UIO.effectTotal(new IntBuffer(intBuffer.slice()))

  override def compact: UIO[Unit] = UIO.effectTotal(intBuffer.compact()).unit

  override def duplicate: UIO[IntBuffer] = UIO.effectTotal(new IntBuffer(intBuffer.duplicate()))

  /**
   * Provides the underlying Java int buffer for use in an effect.
   *
   * This is useful when using Java APIs that require a Java int buffer to be provided.
   *
   * @return The effect value constructed by `f` using the underlying buffer.
   */
  def withJavaBuffer[R, E, A](f: JIntBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(intBuffer)

  override def get: UIO[Int] = UIO.effectTotal(intBuffer.get())

  override def get(i: Int): UIO[Int] = UIO.effectTotal(intBuffer.get(i))

  override def getChunk(maxLength: Int = Int.MaxValue): UIO[Chunk[Int]] =
    UIO.effectTotal {
      val array = Array.ofDim[Int](math.min(maxLength, intBuffer.remaining()))
      intBuffer.get(array)
      Chunk.fromArray(array)
    }

  override def put(element: Int): UIO[Unit] = UIO.effectTotal(intBuffer.put(element)).unit

  override def put(index: Int, element: Int): UIO[Unit] = UIO.effectTotal(intBuffer.put(index, element)).unit

  override protected def putChunkAll(chunk: Chunk[Int]): UIO[Unit] =
    UIO.effectTotal {
      val array = chunk.toArray
      intBuffer.put(array)
    }.unit

  override def asReadOnlyBuffer: UIO[IntBuffer] = UIO.effectTotal(new IntBuffer(intBuffer.asReadOnlyBuffer()))

}
