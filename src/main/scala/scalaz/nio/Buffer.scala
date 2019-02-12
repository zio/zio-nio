package scalaz.nio

import java.nio.{ Buffer => JBuffer, ByteBuffer => JByteBuffer, CharBuffer => JCharBuffer }
import scalaz.zio.{ Chunk, IO }

import scala.reflect.ClassTag

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private[nio] (private[nio] val buffer: JBuffer) {
  final val capacity: IO[Nothing, Int] = IO.succeed(buffer.capacity)

  final def position: IO[Nothing, Int] = IO.sync(buffer.position)

  final def position(newPosition: Int): IO[Exception, Unit] =
    IO.syncException(buffer.position(newPosition)).void

  final def limit: IO[Nothing, Int] = IO.sync(buffer.limit)

  final def remaining: IO[Nothing, Int] = IO.sync(buffer.remaining)

  final def hasRemaining: IO[Nothing, Boolean] = IO.sync(buffer.hasRemaining)

  final def limit(newLimit: Int): IO[Exception, Unit] =
    IO.syncException(buffer.limit(newLimit)).void

  final def mark: IO[Nothing, Unit] = IO.sync(buffer.mark()).void

  final def reset: IO[Exception, Unit] =
    IO.syncException(buffer.reset()).void

  final def clear: IO[Nothing, Unit] = IO.sync(buffer.clear()).void

  final def flip: IO[Nothing, Unit] = IO.sync(buffer.flip()).void

  final def rewind: IO[Nothing, Unit] = IO.sync(buffer.rewind()).void

  final val isReadOnly: IO[Nothing, Boolean] = IO.succeed(buffer.isReadOnly)

  def array: IO[Exception, Array[A]]

  final val hasArray: IO[Nothing, Boolean]  = IO.succeed(buffer.hasArray)
  final def arrayOffset: IO[Exception, Int] = IO.syncException(buffer.arrayOffset)
  final val isDirect: IO[Nothing, Boolean]  = IO.succeed(buffer.isDirect)

}

object Buffer {
  private[this] class ByteBuffer(val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {

    def array: IO[Exception, Array[Byte]] = IO.syncException(byteBuffer.array())
  }

  private[this] class CharBuffer(val charBuffer: JCharBuffer) extends Buffer[Char](charBuffer) {

    def array: IO[Exception, Array[Char]] =
      IO.syncException(charBuffer.array())
  }

  def byte(capacity: Int): IO[Exception, Buffer[Byte]] =
    IO.syncException(JByteBuffer.allocate(capacity)).map(new ByteBuffer(_))

  def byte(chunk: Chunk[Byte]): IO[Exception, Buffer[Byte]] =
    IO.syncException(JByteBuffer.wrap(chunk.toArray)).map(new ByteBuffer(_))

  def char(capacity: Int): IO[Exception, Buffer[Char]] =
    IO.syncException(JCharBuffer.allocate(capacity)).map(new CharBuffer(_))
}
