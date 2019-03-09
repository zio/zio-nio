package scalaz.nio

import scalaz.zio.{ IO, JustExceptions }

import java.nio.{ ByteOrder, DoubleBuffer => JDoubleBuffer }

private[nio] class DoubleBuffer(val doubleBuffer: JDoubleBuffer)
    extends Buffer[Double](doubleBuffer) {

  override def array: IO[Exception, Array[Double]] =
    IO.effect(doubleBuffer.array()).refineOrDie(JustExceptions)

  def order: IO[Nothing, ByteOrder] = IO.succeed(doubleBuffer.order())

  def slice: IO[Exception, DoubleBuffer] =
    IO.effect(doubleBuffer.slice()).map(new DoubleBuffer(_)).refineOrDie(JustExceptions)

  override def get: IO[Exception, Double] =
    IO.effect(doubleBuffer.get()).refineOrDie(JustExceptions)

  override def get(i: Int): IO[Exception, Double] =
    IO.effect(doubleBuffer.get(i)).refineOrDie(JustExceptions)

  override def put(element: Double): IO[Exception, DoubleBuffer] =
    IO.effect(doubleBuffer.put(element)).map(new DoubleBuffer(_)).refineOrDie(JustExceptions)

  override def put(index: Int, element: Double): IO[Exception, DoubleBuffer] =
    IO.effect(doubleBuffer.put(index, element)).map(new DoubleBuffer(_)).refineOrDie(JustExceptions)

  override def asReadOnlyBuffer: IO[Exception, DoubleBuffer] =
    IO.effect(doubleBuffer.asReadOnlyBuffer()).map(new DoubleBuffer(_)).refineOrDie(JustExceptions)
}
