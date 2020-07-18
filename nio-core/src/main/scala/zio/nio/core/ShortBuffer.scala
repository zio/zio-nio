package zio.nio.core

import zio.{ Chunk, IO, ZIO }
import java.nio.{ BufferUnderflowException, ByteOrder, ReadOnlyBufferException, ShortBuffer => JShortBuffer }

final class ShortBuffer(val shortBuffer: JShortBuffer) extends Buffer[Short](shortBuffer) {

  override protected[nio] def array: IO[Exception, Array[Short]] =
    IO.effect(shortBuffer.array()).refineToOrDie[Exception]

  override def order: ByteOrder = shortBuffer.order()

  override def slice: IO[Nothing, ShortBuffer] =
    IO.effectTotal(shortBuffer.slice()).map(new ShortBuffer(_))

  override def compact: IO[ReadOnlyBufferException, Unit] =
    IO.effect(shortBuffer.compact()).unit.refineToOrDie[ReadOnlyBufferException]

  override def duplicate: IO[Nothing, ShortBuffer] =
    IO.effectTotal(new ShortBuffer(shortBuffer.duplicate()))

  def withJavaBuffer[R, E, A](f: JShortBuffer => ZIO[R, E, A]): ZIO[R, E, A] = f(shortBuffer)

  override def get: IO[BufferUnderflowException, Short] =
    IO.effect(shortBuffer.get()).refineToOrDie[BufferUnderflowException]

  override def get(i: Int): IO[IndexOutOfBoundsException, Short] =
    IO.effect(shortBuffer.get(i)).refineToOrDie[IndexOutOfBoundsException]

  override def getChunk(maxLength: Int): IO[BufferUnderflowException, Chunk[Short]] =
    IO.effect {
      val array = Array.ofDim[Short](math.min(maxLength, shortBuffer.remaining()))
      shortBuffer.get(array)
      Chunk.fromArray(array)
    }.refineToOrDie[BufferUnderflowException]

  override def put(element: Short): IO[Exception, Unit] =
    IO.effect(shortBuffer.put(element)).unit.refineToOrDie[Exception]

  override def put(index: Int, element: Short): IO[Exception, Unit] =
    IO.effect(shortBuffer.put(index, element)).unit.refineToOrDie[Exception]

  override def putChunk(chunk: Chunk[Short]): IO[Exception, Unit] =
    IO.effect {
      val array = chunk.toArray
      shortBuffer.put(array)
    }.unit
      .refineToOrDie[Exception]

  override def asReadOnlyBuffer: IO[Nothing, ShortBuffer] =
    IO.effectTotal(shortBuffer.asReadOnlyBuffer()).map(new ShortBuffer(_))
}
