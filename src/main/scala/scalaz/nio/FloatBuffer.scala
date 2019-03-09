package scalaz.nio

import scalaz.zio.{ IO, JustExceptions }

import java.nio.{ ByteOrder, FloatBuffer => JFloatBuffer }

private[nio] class FloatBuffer(val floatBuffer: JFloatBuffer) extends Buffer[Float](floatBuffer) {

  override def array: IO[Exception, Array[Float]] =
    IO.effect(floatBuffer.array()).refineOrDie(JustExceptions)

  def order: IO[Nothing, ByteOrder] = IO.succeed(floatBuffer.order())

  def slice: IO[Exception, FloatBuffer] =
    IO.effect(floatBuffer.slice()).map(new FloatBuffer(_)).refineOrDie(JustExceptions)

  override def get: IO[Exception, Float] = IO.effect(floatBuffer.get()).refineOrDie(JustExceptions)

  override def get(i: Int): IO[Exception, Float] =
    IO.effect(floatBuffer.get(i)).refineOrDie(JustExceptions)

  override def put(element: Float): IO[Exception, FloatBuffer] =
    IO.effect(floatBuffer.put(element)).map(new FloatBuffer(_)).refineOrDie(JustExceptions)

  override def put(index: Int, element: Float): IO[Exception, FloatBuffer] =
    IO.effect(floatBuffer.put(index, element)).map(new FloatBuffer(_)).refineOrDie(JustExceptions)

  override def asReadOnlyBuffer: IO[Exception, FloatBuffer] =
    IO.effect(floatBuffer.asReadOnlyBuffer()).map(new FloatBuffer(_)).refineOrDie(JustExceptions)
}
