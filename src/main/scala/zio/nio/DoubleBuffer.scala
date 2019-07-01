package zio.nio

import zio.IO

import java.nio.{ ByteOrder, DoubleBuffer => JDoubleBuffer }

private[nio] class DoubleBuffer(val doubleBuffer: JDoubleBuffer)
    extends Buffer[Double](doubleBuffer) {

  override val array: IO[Exception, Array[Double]] =
    IO.effect(doubleBuffer.array()).refineToOrDie[Exception]

  val order: IO[Nothing, ByteOrder] = IO.succeed(doubleBuffer.order())

  val slice: IO[Exception, DoubleBuffer] =
    IO.effect(doubleBuffer.slice()).map(new DoubleBuffer(_)).refineToOrDie[Exception]

  override val get: IO[Exception, Double] =
    IO.effect(doubleBuffer.get()).refineToOrDie[Exception]

  override def get(i: Int): IO[Exception, Double] =
    IO.effect(doubleBuffer.get(i)).refineToOrDie[Exception]

  override def put(element: Double): IO[Exception, DoubleBuffer] =
    IO.effect(doubleBuffer.put(element)).map(new DoubleBuffer(_)).refineToOrDie[Exception]

  override def put(index: Int, element: Double): IO[Exception, DoubleBuffer] =
    IO.effect(doubleBuffer.put(index, element)).map(new DoubleBuffer(_)).refineToOrDie[Exception]

  override val asReadOnlyBuffer: IO[Exception, DoubleBuffer] =
    IO.effect(doubleBuffer.asReadOnlyBuffer()).map(new DoubleBuffer(_)).refineToOrDie[Exception]
}
