package scalaz.nio

import scalaz.zio.{ IO, JustExceptions }

import java.nio.{ ByteOrder, LongBuffer => JLongBuffer }

private[nio] class LongBuffer(val longBuffer: JLongBuffer) extends Buffer[Long](longBuffer) {

  override def array: IO[Exception, Array[Long]] =
    IO.effect(longBuffer.array()).refineOrDie(JustExceptions)

  def order: IO[Nothing, ByteOrder] = IO.succeed(longBuffer.order())

  def slice: IO[Exception, LongBuffer] =
    IO.effect(longBuffer.slice()).map(new LongBuffer(_)).refineOrDie(JustExceptions)

  override def get: IO[Exception, Long] = IO.effect(longBuffer.get()).refineOrDie(JustExceptions)

  override def get(i: Int): IO[Exception, Long] =
    IO.effect(longBuffer.get(i)).refineOrDie(JustExceptions)

  override def put(element: Long): IO[Exception, LongBuffer] =
    IO.effect(longBuffer.put(element)).map(new LongBuffer(_)).refineOrDie(JustExceptions)

  override def put(index: Int, element: Long): IO[Exception, LongBuffer] =
    IO.effect(longBuffer.put(index, element)).map(new LongBuffer(_)).refineOrDie(JustExceptions)

  override def asReadOnlyBuffer: IO[Exception, LongBuffer] =
    IO.effect(longBuffer.asReadOnlyBuffer()).map(new LongBuffer(_)).refineOrDie(JustExceptions)
}
