package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, LongBuffer => JLongBuffer }

private[nio] class LongBuffer(val longBuffer: JLongBuffer) extends Buffer[Long](longBuffer) {

  override def array: IO[Exception, Array[Long]] = IO.syncException(longBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.succeed(longBuffer.order())

  def slice: IO[Exception, LongBuffer] = IO.syncException(longBuffer.slice()).map(new LongBuffer(_))

  override def get: IO[Exception, Long] = IO.syncException(longBuffer.get())

  override def get(i: Int): IO[Exception, Long] = IO.syncException(longBuffer.get(i))

  override def put(element: Long): IO[Exception, LongBuffer] =
    IO.syncException(longBuffer.put(element)).map(new LongBuffer(_))

  override def put(index: Int, element: Long): IO[Exception, LongBuffer] =
    IO.syncException(longBuffer.put(index, element)).map(new LongBuffer(_))

  override def asReadOnlyBuffer: IO[Exception, LongBuffer] =
    IO.syncException(longBuffer.asReadOnlyBuffer()).map(new LongBuffer(_))
}
