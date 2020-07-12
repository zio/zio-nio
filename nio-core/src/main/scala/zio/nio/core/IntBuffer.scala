package zio.nio.core

import zio.{ Chunk, IO, ZIO }
import java.nio.{ BufferUnderflowException, ByteOrder, ReadOnlyBufferException, IntBuffer => JIntBuffer }

final class IntBuffer(val intBuffer: JIntBuffer) extends Buffer[Int](intBuffer) {

  override protected[nio] def array: IO[Exception, Array[Int]] =
    IO.effect(intBuffer.array()).refineToOrDie[Exception]

  override def order: ByteOrder = intBuffer.order

  override def slice: IO[Nothing, IntBuffer] =
    IO.effectTotal(intBuffer.slice()).map(new IntBuffer(_))

  override def compact: IO[ReadOnlyBufferException, Unit] =
    IO.effect(intBuffer.compact()).unit.refineToOrDie[ReadOnlyBufferException]

  override def duplicate: IO[Nothing, IntBuffer] =
    IO.effectTotal(new IntBuffer(intBuffer.duplicate()))

  def withJavaBuffer[R, E, A](f: JIntBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(intBuffer)

  override def get: IO[BufferUnderflowException, Int] =
    IO.effect(intBuffer.get()).refineToOrDie[BufferUnderflowException]

  override def get(i: Int): IO[IndexOutOfBoundsException, Int] =
    IO.effect(intBuffer.get(i)).refineToOrDie[IndexOutOfBoundsException]

  override def getChunk(maxLength: Int = Int.MaxValue): IO[BufferUnderflowException, Chunk[Int]] =
    IO.effect {
      val array = Array.ofDim[Int](math.min(maxLength, intBuffer.remaining()))
      intBuffer.get(array)
      Chunk.fromArray(array)
    }.refineToOrDie[BufferUnderflowException]

  override def put(element: Int): IO[Exception, Unit] =
    IO.effect(intBuffer.put(element)).unit.refineToOrDie[Exception]

  override def put(index: Int, element: Int): IO[Exception, Unit] =
    IO.effect(intBuffer.put(index, element)).unit.refineToOrDie[Exception]

  override def putChunk(chunk: Chunk[Int]): IO[Exception, Unit] =
    IO.effect {
      val array = chunk.toArray
      intBuffer.put(array)
    }.unit
      .refineToOrDie[Exception]

  override def asReadOnlyBuffer: IO[Nothing, IntBuffer] =
    IO.effectTotal(intBuffer.asReadOnlyBuffer()).map(new IntBuffer(_))
}
