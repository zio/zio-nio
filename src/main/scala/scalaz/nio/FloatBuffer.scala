package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, FloatBuffer => JFloatBuffer }

private[nio] class FloatBuffer(val floatBuffer: JFloatBuffer) extends Buffer[Float](floatBuffer) {

  override def array: IO[Exception, Array[Float]] = IO.syncException(floatBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.succeed(floatBuffer.order())

  def slice: IO[Exception, FloatBuffer] =
    IO.syncException(floatBuffer.slice()).map(new FloatBuffer(_))

  override def get: IO[Exception, Float] = IO.syncException(floatBuffer.get())

  override def get(i: Int): IO[Exception, Float] = IO.syncException(floatBuffer.get(i))

  override def put(element: Float): IO[Exception, FloatBuffer] =
    IO.syncException(floatBuffer.put(element)).map(new FloatBuffer(_))

  override def put(index: Int, element: Float): IO[Exception, FloatBuffer] =
    IO.syncException(floatBuffer.put(index, element)).map(new FloatBuffer(_))

  override def asReadOnlyBuffer: IO[Exception, FloatBuffer] =
    IO.syncException(floatBuffer.asReadOnlyBuffer()).map(new FloatBuffer(_))
}
