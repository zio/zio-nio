package zio.nio

import zio.IO

import java.nio.{ ByteOrder, FloatBuffer => JFloatBuffer }

private[nio] class FloatBuffer(val floatBuffer: JFloatBuffer) extends Buffer[Float](floatBuffer) {

  override val array: IO[Exception, Array[Float]] =
    IO.effect(floatBuffer.array()).refineToOrDie[Exception]

  def order: IO[Nothing, ByteOrder] = IO.succeed(floatBuffer.order())

  def slice: IO[Exception, FloatBuffer] =
    IO.effect(floatBuffer.slice()).map(new FloatBuffer(_)).refineToOrDie[Exception]

  override val get: IO[Exception, Float] = IO.effect(floatBuffer.get()).refineToOrDie[Exception]

  override def get(i: Int): IO[Exception, Float] =
    IO.effect(floatBuffer.get(i)).refineToOrDie[Exception]

  override def put(element: Float): IO[Exception, FloatBuffer] =
    IO.effect(floatBuffer.put(element)).map(new FloatBuffer(_)).refineToOrDie[Exception]

  override def put(index: Int, element: Float): IO[Exception, FloatBuffer] =
    IO.effect(floatBuffer.put(index, element)).map(new FloatBuffer(_)).refineToOrDie[Exception]

  override val asReadOnlyBuffer: IO[Exception, FloatBuffer] =
    IO.effect(floatBuffer.asReadOnlyBuffer()).map(new FloatBuffer(_)).refineToOrDie[Exception]
}
