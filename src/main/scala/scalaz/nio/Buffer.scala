package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, Buffer => JBuffer }

import scala.reflect.ClassTag

@specialized // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag, B <: JBuffer] private[nio] (private[nio] val buffer: B) {

  final def capacity: IO[Nothing, Int] = IO.now(buffer.capacity)

  final def position: IO[Nothing, Int] = IO.now(buffer.position)

  final def position(newPosition: Int): IO[Exception, Unit] =
    IO.syncException(buffer.position(newPosition)).void

  final def limit: IO[Nothing, Int] = IO.now(buffer.limit)

  final def remaining: IO[Nothing, Int] = IO.now(buffer.remaining)

  final def hasRemaining: IO[Nothing, Boolean] = IO.now(buffer.hasRemaining)

  final def limit(newLimit: Int): IO[Exception, Unit] =
    IO.syncException(buffer.limit(newLimit)).void

  final def mark: IO[Nothing, Unit] = IO.sync(buffer.mark()).void

  final def reset: IO[Nothing, Unit] = IO.sync(buffer.reset()).void

  final def clear: IO[Nothing, Unit] = IO.sync(buffer.clear()).void

  final def flip: IO[Nothing, Unit] = IO.sync(buffer.flip()).void

  final def rewind: IO[Nothing, Unit] = IO.sync(buffer.rewind()).void

  final def isReadOnly: IO[Nothing, Boolean] = IO.now(buffer.isReadOnly)

  final def hasArray: IO[Nothing, Boolean] = IO.now(buffer.hasArray)

  final def arrayOffset: IO[Nothing, Int] = IO.now(buffer.arrayOffset)

  final def isDirect: IO[Nothing, Boolean] = IO.now(buffer.isDirect)

  // Following operations are present in every buffer, but for some reason are not part of Buffer interface.

  type Self <: Buffer[A, B]

  def array: IO[Exception, Array[A]]

  def get: IO[Exception, A]

  def get(i: Int): IO[Exception, A]

  def put(element: A): IO[Exception, Self]

  def put(index: Int, element: A): IO[Exception, Self]

  def order: IO[Nothing, ByteOrder]

  def slice: IO[Exception, Self]

  def asReadOnlyBuffer: IO[Exception, Self]
}

abstract class BufferOps[A: ClassTag, B <: JBuffer, C <: Buffer[A, B]] {

  private[nio] def apply(javaBuffer: B): C

  def allocate(capacity: Int): IO[Exception, C]

  def wrap(array: Array[A]): IO[Exception, C]

  def wrap(array: Array[A], offset: Int, length: Int): IO[Exception, C]
}
