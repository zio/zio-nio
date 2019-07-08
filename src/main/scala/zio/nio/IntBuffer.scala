package zio.nio

import zio.IO

import java.nio.{ ByteOrder, IntBuffer => JIntBuffer }

private[nio] class IntBuffer(val intBuffer: JIntBuffer) extends Buffer[Int](intBuffer) {

  override val array: IO[Exception, Array[Int]] =
    IO.effect(intBuffer.array()).refineToOrDie[Exception]

  def order: IO[Nothing, ByteOrder] = IO.succeed(intBuffer.order())

  def slice: IO[Exception, IntBuffer] =
    IO.effect(intBuffer.slice()).map(new IntBuffer(_)).refineToOrDie[Exception]

  override val get: IO[Exception, Int] = IO.effect(intBuffer.get()).refineToOrDie[Exception]

  override def get(i: Int): IO[Exception, Int] =
    IO.effect(intBuffer.get(i)).refineToOrDie[Exception]

  override def put(element: Int): IO[Exception, IntBuffer] =
    IO.effect(intBuffer.put(element)).map(new IntBuffer(_)).refineToOrDie[Exception]

  override def put(index: Int, element: Int): IO[Exception, IntBuffer] =
    IO.effect(intBuffer.put(index, element)).map(new IntBuffer(_)).refineToOrDie[Exception]

  override val asReadOnlyBuffer: IO[Exception, IntBuffer] =
    IO.effect(intBuffer.asReadOnlyBuffer()).map(new IntBuffer(_)).refineToOrDie[Exception]
}
