package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, ShortBuffer => JShortBuffer }

class ShortBuffer private (private[nio] val javaBuffer: JShortBuffer)
    extends Buffer[Short, JShortBuffer](javaBuffer) {

  type Self = ShortBuffer

  def array: IO[Exception, Array[Short]] = IO.syncException(javaBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.now(javaBuffer.order())

  def slice: IO[Exception, ShortBuffer] =
    IO.syncException(javaBuffer.slice()).map(new ShortBuffer(_))

  def get: IO[Exception, Short] = IO.syncException(javaBuffer.get())

  def get(i: Int): IO[Exception, Short] = IO.syncException(javaBuffer.get(i))

  def put(element: Short): IO[Exception, ShortBuffer] =
    IO.syncException(javaBuffer.put(element)).map(new ShortBuffer(_))

  def put(index: Int, element: Short): IO[Exception, ShortBuffer] =
    IO.syncException(javaBuffer.put(index, element)).map(new ShortBuffer(_))

  def asReadOnlyBuffer: IO[Exception, ShortBuffer] =
    IO.syncException(javaBuffer.asReadOnlyBuffer()).map(new ShortBuffer(_))
}

object ShortBuffer extends BufferOps[Short, JShortBuffer, ShortBuffer] {

  private[nio] def apply(javaBuffer: JShortBuffer): ShortBuffer = new ShortBuffer(javaBuffer)

  def allocate(capacity: Int): IO[Exception, ShortBuffer] =
    IO.syncException(JShortBuffer.allocate(capacity)).map(new ShortBuffer(_))

  def wrap(array: Array[Short]): IO[Exception, ShortBuffer] =
    IO.syncException(JShortBuffer.wrap(array)).map(new ShortBuffer(_))

  def wrap(array: Array[Short], offset: Int, length: Int): IO[Exception, ShortBuffer] =
    IO.syncException(JShortBuffer.wrap(array, offset, length)).map(new ShortBuffer(_))
}
