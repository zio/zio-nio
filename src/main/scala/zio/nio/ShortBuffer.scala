package zio.nio

import zio.IO

import java.nio.{ ByteOrder, ShortBuffer => JShortBuffer }

private[nio] class ShortBuffer(val shortBuffer: JShortBuffer) extends Buffer[Short](shortBuffer) {

  override val array: IO[Exception, Array[Short]] =
    IO.effect(shortBuffer.array()).refineToOrDie[Exception]

  def order: IO[Nothing, ByteOrder] = IO.succeed(shortBuffer.order())

  def slice: IO[Exception, ShortBuffer] =
    IO.effect(shortBuffer.slice()).map(new ShortBuffer(_)).refineToOrDie[Exception]

  override val get: IO[Exception, Short] = IO.effect(shortBuffer.get()).refineToOrDie[Exception]

  override def get(i: Int): IO[Exception, Short] =
    IO.effect(shortBuffer.get(i)).refineToOrDie[Exception]

  override def put(element: Short): IO[Exception, ShortBuffer] =
    IO.effect(shortBuffer.put(element)).map(new ShortBuffer(_)).refineToOrDie[Exception]

  override def put(index: Int, element: Short): IO[Exception, ShortBuffer] =
    IO.effect(shortBuffer.put(index, element)).map(new ShortBuffer(_)).refineToOrDie[Exception]

  override val asReadOnlyBuffer: IO[Exception, ShortBuffer] =
    IO.effect(shortBuffer.asReadOnlyBuffer()).map(new ShortBuffer(_)).refineToOrDie[Exception]
}
