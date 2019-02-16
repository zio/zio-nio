package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, LongBuffer => JLongBuffer }

class LongBuffer private (private[nio] val javaBuffer: JLongBuffer)
    extends Buffer[Long, JLongBuffer](javaBuffer) {

  type Self = LongBuffer

  def array: IO[Exception, Array[Long]] = IO.syncException(javaBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.now(javaBuffer.order())

  def slice: IO[Exception, LongBuffer] = IO.syncException(javaBuffer.slice()).map(new LongBuffer(_))

  def get: IO[Exception, Long] = IO.syncException(javaBuffer.get())

  def get(i: Int): IO[Exception, Long] = IO.syncException(javaBuffer.get(i))

  def put(element: Long): IO[Exception, LongBuffer] =
    IO.syncException(javaBuffer.put(element)).map(new LongBuffer(_))

  def put(index: Int, element: Long): IO[Exception, LongBuffer] =
    IO.syncException(javaBuffer.put(index, element)).map(new LongBuffer(_))

  def asReadOnlyBuffer: IO[Exception, LongBuffer] =
    IO.syncException(javaBuffer.asReadOnlyBuffer()).map(new LongBuffer(_))
}

object LongBuffer extends BufferOps[Long, JLongBuffer, LongBuffer] {

  private[nio] def apply(javaBuffer: JLongBuffer): LongBuffer = new LongBuffer(javaBuffer)

  def allocate(capacity: Int): IO[Exception, LongBuffer] =
    IO.syncException(JLongBuffer.allocate(capacity)).map(new LongBuffer(_))

  def wrap(array: Array[Long]): IO[Exception, LongBuffer] =
    IO.syncException(JLongBuffer.wrap(array)).map(new LongBuffer(_))

  def wrap(array: Array[Long], offset: Int, length: Int): IO[Exception, LongBuffer] =
    IO.syncException(JLongBuffer.wrap(array, offset, length)).map(new LongBuffer(_))
}
