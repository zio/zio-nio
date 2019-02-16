package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, DoubleBuffer => JDoubleBuffer }

class DoubleBuffer private (private[nio] val javaBuffer: JDoubleBuffer)
    extends Buffer[Double, JDoubleBuffer](javaBuffer) {

  type Self = DoubleBuffer

  def array: IO[Exception, Array[Double]] = IO.syncException(javaBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.now(javaBuffer.order())

  def slice: IO[Exception, DoubleBuffer] =
    IO.syncException(javaBuffer.slice()).map(new DoubleBuffer(_))

  def get: IO[Exception, Double] = IO.syncException(javaBuffer.get())

  def get(i: Int): IO[Exception, Double] = IO.syncException(javaBuffer.get(i))

  def put(element: Double): IO[Exception, DoubleBuffer] =
    IO.syncException(javaBuffer.put(element)).map(new DoubleBuffer(_))

  def put(index: Int, element: Double): IO[Exception, DoubleBuffer] =
    IO.syncException(javaBuffer.put(index, element)).map(new DoubleBuffer(_))

  def asReadOnlyBuffer: IO[Exception, DoubleBuffer] =
    IO.syncException(javaBuffer.asReadOnlyBuffer()).map(new DoubleBuffer(_))
}

object DoubleBuffer extends BufferOps[Double, JDoubleBuffer, DoubleBuffer] {

  private[nio] def apply(javaBuffer: JDoubleBuffer): DoubleBuffer = new DoubleBuffer(javaBuffer)

  def allocate(capacity: Int): IO[Exception, DoubleBuffer] =
    IO.syncException(JDoubleBuffer.allocate(capacity)).map(new DoubleBuffer(_))

  def wrap(array: Array[Double]): IO[Exception, DoubleBuffer] =
    IO.syncException(JDoubleBuffer.wrap(array)).map(new DoubleBuffer(_))

  def wrap(array: Array[Double], offset: Int, length: Int): IO[Exception, DoubleBuffer] =
    IO.syncException(JDoubleBuffer.wrap(array, offset, length)).map(new DoubleBuffer(_))
}
