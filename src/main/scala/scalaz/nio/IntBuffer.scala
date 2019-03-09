package scalaz.nio

import scalaz.zio.{ IO, JustExceptions }

import java.nio.{ ByteOrder, IntBuffer => JIntBuffer }

private[nio] class IntBuffer(val intBuffer: JIntBuffer) extends Buffer[Int](intBuffer) {

  override def array: IO[Exception, Array[Int]] =
    IO.effect(intBuffer.array()).refineOrDie(JustExceptions)

  def order: IO[Nothing, ByteOrder] = IO.succeed(intBuffer.order())

  def slice: IO[Exception, IntBuffer] =
    IO.effect(intBuffer.slice()).map(new IntBuffer(_)).refineOrDie(JustExceptions)

  override def get: IO[Exception, Int] = IO.effect(intBuffer.get()).refineOrDie(JustExceptions)

  override def get(i: Int): IO[Exception, Int] =
    IO.effect(intBuffer.get(i)).refineOrDie(JustExceptions)

  override def put(element: Int): IO[Exception, IntBuffer] =
    IO.effect(intBuffer.put(element)).map(new IntBuffer(_)).refineOrDie(JustExceptions)

  override def put(index: Int, element: Int): IO[Exception, IntBuffer] =
    IO.effect(intBuffer.put(index, element)).map(new IntBuffer(_)).refineOrDie(JustExceptions)

  override def asReadOnlyBuffer: IO[Exception, IntBuffer] =
    IO.effect(intBuffer.asReadOnlyBuffer()).map(new IntBuffer(_)).refineOrDie(JustExceptions)
}
