package zio.nio

import zio.IO

import java.nio.{ ByteOrder, LongBuffer => JLongBuffer }

private[nio] class LongBuffer(val longBuffer: JLongBuffer) extends Buffer[Long](longBuffer) {

  override val array: IO[Exception, Array[Long]] =
    IO.effect(longBuffer.array()).refineToOrDie[Exception]

  def order: IO[Nothing, ByteOrder] = IO.succeed(longBuffer.order())

  def slice: IO[Exception, LongBuffer] =
    IO.effect(longBuffer.slice()).map(new LongBuffer(_)).refineToOrDie[Exception]

  override val get: IO[Exception, Long] = IO.effect(longBuffer.get()).refineToOrDie[Exception]

  override def get(i: Int): IO[Exception, Long] =
    IO.effect(longBuffer.get(i)).refineToOrDie[Exception]

  override def put(element: Long): IO[Exception, LongBuffer] =
    IO.effect(longBuffer.put(element)).map(new LongBuffer(_)).refineToOrDie[Exception]

  override def put(index: Int, element: Long): IO[Exception, LongBuffer] =
    IO.effect(longBuffer.put(index, element)).map(new LongBuffer(_)).refineToOrDie[Exception]

  override val asReadOnlyBuffer: IO[Exception, LongBuffer] =
    IO.effect(longBuffer.asReadOnlyBuffer()).map(new LongBuffer(_)).refineToOrDie[Exception]
}
