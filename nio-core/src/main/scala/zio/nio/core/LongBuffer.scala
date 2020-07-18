package zio.nio.core

import zio.{ Chunk, IO, ZIO }
import java.nio.{ BufferUnderflowException, ByteOrder, ReadOnlyBufferException, LongBuffer => JLongBuffer }

final class LongBuffer(val longBuffer: JLongBuffer) extends Buffer[Long](longBuffer) {

  override protected[nio] def array: IO[Exception, Array[Long]] =
    IO.effect(longBuffer.array()).refineToOrDie[Exception]

  override def order: ByteOrder = longBuffer.order

  override def slice: IO[Nothing, LongBuffer] =
    IO.effectTotal(longBuffer.slice()).map(new LongBuffer(_))

  override def compact: IO[ReadOnlyBufferException, Unit] =
    IO.effect(longBuffer.compact()).unit.refineToOrDie[ReadOnlyBufferException]

  override def duplicate: IO[Nothing, LongBuffer] =
    IO.effectTotal(new LongBuffer(longBuffer.duplicate()))

  def withJavaBuffer[R, E, A](f: JLongBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(longBuffer)

  override def get: IO[BufferUnderflowException, Long] =
    IO.effect(longBuffer.get()).refineToOrDie[BufferUnderflowException]

  override def get(i: Int): IO[IndexOutOfBoundsException, Long] =
    IO.effect(longBuffer.get(i)).refineToOrDie[IndexOutOfBoundsException]

  override def getChunk(maxLength: Int = Int.MaxValue): IO[BufferUnderflowException, Chunk[Long]] =
    IO.effect {
      val array = Array.ofDim[Long](math.min(maxLength, longBuffer.remaining()))
      longBuffer.get(array)
      Chunk.fromArray(array)
    }.refineToOrDie[BufferUnderflowException]

  override def put(element: Long): IO[Exception, Unit] =
    IO.effect(longBuffer.put(element)).unit.refineToOrDie[Exception]

  override def put(index: Int, element: Long): IO[Exception, Unit] =
    IO.effect(longBuffer.put(index, element)).unit.refineToOrDie[Exception]

  override def putChunk(chunk: Chunk[Long]): IO[Exception, Unit] =
    IO.effect {
      val array = chunk.toArray
      longBuffer.put(array)
    }.unit
      .refineToOrDie[Exception]

  override def asReadOnlyBuffer: IO[Nothing, LongBuffer] =
    IO.effectTotal(longBuffer.asReadOnlyBuffer()).map(new LongBuffer(_))
}
