package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, DoubleBuffer => JDoubleBuffer }

private[nio] class DoubleBuffer(val doubleBuffer: JDoubleBuffer)
    extends Buffer[Double](doubleBuffer) {

  override def array: IO[Exception, Array[Double]] = IO.syncException(doubleBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.succeed(doubleBuffer.order())

  def slice: IO[Exception, DoubleBuffer] =
    IO.syncException(doubleBuffer.slice()).map(new DoubleBuffer(_))

  def get: IO[Exception, Double] = IO.syncException(doubleBuffer.get())

  def get(i: Int): IO[Exception, Double] = IO.syncException(doubleBuffer.get(i))

  def put(element: Double): IO[Exception, DoubleBuffer] =
    IO.syncException(doubleBuffer.put(element)).map(new DoubleBuffer(_))

  def put(index: Int, element: Double): IO[Exception, DoubleBuffer] =
    IO.syncException(doubleBuffer.put(index, element)).map(new DoubleBuffer(_))

  def asReadOnlyBuffer: IO[Exception, DoubleBuffer] =
    IO.syncException(doubleBuffer.asReadOnlyBuffer()).map(new DoubleBuffer(_))
}
