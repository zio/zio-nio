package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, FloatBuffer => JFloatBuffer }

class FloatBuffer private (private[nio] val javaBuffer: JFloatBuffer)
    extends Buffer[Float, JFloatBuffer](javaBuffer) {

  type Self = FloatBuffer

  def array: IO[Exception, Array[Float]] = IO.syncException(javaBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.now(javaBuffer.order())

  def slice: IO[Exception, FloatBuffer] =
    IO.syncException(javaBuffer.slice()).map(new FloatBuffer(_))

  def get: IO[Exception, Float] = IO.syncException(javaBuffer.get())

  def get(i: Int): IO[Exception, Float] = IO.syncException(javaBuffer.get(i))

  def put(element: Float): IO[Exception, FloatBuffer] =
    IO.syncException(javaBuffer.put(element)).map(new FloatBuffer(_))

  def put(index: Int, element: Float): IO[Exception, FloatBuffer] =
    IO.syncException(javaBuffer.put(index, element)).map(new FloatBuffer(_))

  def asReadOnlyBuffer: IO[Exception, FloatBuffer] =
    IO.syncException(javaBuffer.asReadOnlyBuffer()).map(new FloatBuffer(_))
}

object FloatBuffer extends BufferOps[Float, JFloatBuffer, FloatBuffer] {

  private[nio] def apply(javaBuffer: JFloatBuffer): FloatBuffer = new FloatBuffer(javaBuffer)

  def allocate(capacity: Int): IO[Exception, FloatBuffer] =
    IO.syncException(JFloatBuffer.allocate(capacity)).map(new FloatBuffer(_))

  def wrap(array: Array[Float]): IO[Exception, FloatBuffer] =
    IO.syncException(JFloatBuffer.wrap(array)).map(new FloatBuffer(_))

  def wrap(array: Array[Float], offset: Int, length: Int): IO[Exception, FloatBuffer] =
    IO.syncException(JFloatBuffer.wrap(array, offset, length)).map(new FloatBuffer(_))
}
