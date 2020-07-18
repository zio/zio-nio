package zio.nio.core

import zio.{ Chunk, IO, ZIO }
import java.nio.{ BufferUnderflowException, ByteOrder, ReadOnlyBufferException, DoubleBuffer => JDoubleBuffer }

final class DoubleBuffer(doubleBuffer: JDoubleBuffer) extends Buffer[Double](doubleBuffer) {

  override protected[nio] def array: IO[Exception, Array[Double]] =
    IO.effect(doubleBuffer.array()).refineToOrDie[Exception]

  override def order: ByteOrder = doubleBuffer.order

  override def slice: IO[Nothing, DoubleBuffer] =
    IO.effectTotal(doubleBuffer.slice()).map(new DoubleBuffer(_))

  override def compact: IO[ReadOnlyBufferException, Unit] =
    IO.effect(doubleBuffer.compact()).unit.refineToOrDie[ReadOnlyBufferException]

  override def duplicate: IO[Nothing, DoubleBuffer] =
    IO.effectTotal(new DoubleBuffer(doubleBuffer.duplicate()))

  def withJavaBuffer[R, E, A](f: JDoubleBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(doubleBuffer)

  override def get: IO[BufferUnderflowException, Double] =
    IO.effect(doubleBuffer.get()).refineToOrDie[BufferUnderflowException]

  override def get(i: Int): IO[IndexOutOfBoundsException, Double] =
    IO.effect(doubleBuffer.get(i)).refineToOrDie[IndexOutOfBoundsException]

  override def getChunk(
    maxLength: Int = Int.MaxValue
  ): IO[BufferUnderflowException, Chunk[Double]] =
    IO.effect {
      val array = Array.ofDim[Double](math.min(maxLength, doubleBuffer.remaining()))
      doubleBuffer.get(array)
      Chunk.fromArray(array)
    }.refineToOrDie[BufferUnderflowException]

  override def put(element: Double): IO[Exception, Unit] =
    IO.effect(doubleBuffer.put(element)).unit.refineToOrDie[Exception]

  override def put(index: Int, element: Double): IO[Exception, Unit] =
    IO.effect(doubleBuffer.put(index, element)).unit.refineToOrDie[Exception]

  override def putChunk(chunk: Chunk[Double]): IO[Exception, Unit] =
    IO.effect {
      val array = chunk.toArray
      doubleBuffer.put(array)
    }.unit
      .refineToOrDie[Exception]

  override def asReadOnlyBuffer: IO[Nothing, DoubleBuffer] =
    IO.effectTotal(doubleBuffer.asReadOnlyBuffer()).map(new DoubleBuffer(_))
}
