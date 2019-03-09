package scalaz.nio

import scalaz.zio.{ IO, JustExceptions }

import java.nio.{ ByteOrder, ShortBuffer => JShortBuffer }

private[nio] class ShortBuffer(val shortBuffer: JShortBuffer) extends Buffer[Short](shortBuffer) {

  override def array: IO[Exception, Array[Short]] =
    IO.effect(shortBuffer.array()).refineOrDie(JustExceptions)

  def order: IO[Nothing, ByteOrder] = IO.succeed(shortBuffer.order())

  def slice: IO[Exception, ShortBuffer] =
    IO.effect(shortBuffer.slice()).map(new ShortBuffer(_)).refineOrDie(JustExceptions)

  override def get: IO[Exception, Short] = IO.effect(shortBuffer.get()).refineOrDie(JustExceptions)

  override def get(i: Int): IO[Exception, Short] =
    IO.effect(shortBuffer.get(i)).refineOrDie(JustExceptions)

  override def put(element: Short): IO[Exception, ShortBuffer] =
    IO.effect(shortBuffer.put(element)).map(new ShortBuffer(_)).refineOrDie(JustExceptions)

  override def put(index: Int, element: Short): IO[Exception, ShortBuffer] =
    IO.effect(shortBuffer.put(index, element)).map(new ShortBuffer(_)).refineOrDie(JustExceptions)

  override def asReadOnlyBuffer: IO[Exception, ShortBuffer] =
    IO.effect(shortBuffer.asReadOnlyBuffer()).map(new ShortBuffer(_)).refineOrDie(JustExceptions)
}
