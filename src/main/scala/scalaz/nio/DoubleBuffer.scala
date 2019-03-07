package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, DoubleBuffer => JDoubleBuffer }

private[nio] class DoubleBuffer(val doubleBuffer: JDoubleBuffer)
    extends Buffer[Double](doubleBuffer) {

  override def array: IO[Exception, Array[Double]] = IO.syncException(doubleBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.succeed(doubleBuffer.order())

  def slice: IO[Exception, DoubleBuffer] =
    IO.syncException(doubleBuffer.slice()).map(new DoubleBuffer(_))

  override def get: IO[Exception, Double] = IO.syncException(doubleBuffer.get())

  override def get(i: Int): IO[Exception, Double] = IO.syncException(doubleBuffer.get(i))

  override def put(element: Double): IO[Exception, DoubleBuffer] =
    IO.syncException(doubleBuffer.put(element)).map(new DoubleBuffer(_))

  override def put(index: Int, element: Double): IO[Exception, DoubleBuffer] =
    IO.syncException(doubleBuffer.put(index, element)).map(new DoubleBuffer(_))

  override def asReadOnlyBuffer: IO[Exception, DoubleBuffer] =
    IO.syncException(doubleBuffer.asReadOnlyBuffer()).map(new DoubleBuffer(_))
}
