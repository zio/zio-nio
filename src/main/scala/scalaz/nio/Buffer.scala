package scalaz.nio

import java.nio.{ Buffer => JBuffer, ByteBuffer => JByteBuffer, CharBuffer => JCharBuffer }
import scalaz.zio.{ Chunk, IO, JustExceptions, UIO, ZIO }

import scala.reflect.ClassTag

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private[nio] (private[nio] val buffer: JBuffer) {
  final val capacity: UIO[Int] = ZIO.succeed(buffer.capacity)

  final def position: UIO[Int] = IO.effectTotal(buffer.position)

  final def position(newPosition: Int): IO[Exception, Unit] =
    IO.effect(buffer.position(newPosition)).void.refineOrDie(JustExceptions)

  final def limit: UIO[Int] = IO.effectTotal(buffer.limit)

  final def remaining: UIO[Int] = IO.effectTotal(buffer.remaining)

  final def hasRemaining: UIO[Boolean] = IO.effectTotal(buffer.hasRemaining)

  final def limit(newLimit: Int): IO[Exception, Unit] =
    IO.effect(buffer.limit(newLimit)).void.refineOrDie(JustExceptions)

  final def mark: UIO[Unit] = IO.effectTotal(buffer.mark()).void

  final def reset: IO[Exception, Unit] =
    IO.effect(buffer.reset()).void.refineOrDie(JustExceptions)

  final def clear: UIO[Unit] = IO.effectTotal(buffer.clear()).void

  final def flip: UIO[Unit] = IO.effectTotal(buffer.flip()).void

  final def rewind: UIO[Unit] = IO.effectTotal(buffer.rewind()).void

  final val isReadOnly: UIO[Boolean] = IO.succeed(buffer.isReadOnly)

  def array: IO[Exception, Array[A]]

  final val hasArray: UIO[Boolean] = IO.succeed(buffer.hasArray)
  final def arrayOffset: IO[Exception, Int] =
    IO.effect(buffer.arrayOffset).refineOrDie(JustExceptions)
  final val isDirect: UIO[Boolean] = IO.succeed(buffer.isDirect)

}

object Buffer {
  private[this] class ByteBuffer(val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {

    def array: IO[Exception, Array[Byte]] =
      IO.effect(byteBuffer.array()).refineOrDie(JustExceptions)
  }

  private[this] class CharBuffer(val charBuffer: JCharBuffer) extends Buffer[Char](charBuffer) {

    def array: IO[Exception, Array[Char]] =
      IO.effect(charBuffer.array()).refineOrDie(JustExceptions)
  }

  def byte(capacity: Int): IO[Exception, Buffer[Byte]] =
    IO.effect(JByteBuffer.allocate(capacity)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def byte(chunk: Chunk[Byte]): IO[Exception, Buffer[Byte]] =
    IO.effect(JByteBuffer.wrap(chunk.toArray)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def char(capacity: Int): IO[Exception, Buffer[Char]] =
    IO.effect(JCharBuffer.allocate(capacity)).map(new CharBuffer(_)).refineOrDie(JustExceptions)
}
