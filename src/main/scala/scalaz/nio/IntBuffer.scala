package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, IntBuffer => JIntBuffer }

private[nio] class IntBuffer(val intBuffer: JIntBuffer) extends Buffer[Int](intBuffer) {

  override def array: IO[Exception, Array[Int]] = IO.syncException(intBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.succeed(intBuffer.order())

  def slice: IO[Exception, IntBuffer] = IO.syncException(intBuffer.slice()).map(new IntBuffer(_))

  def get: IO[Exception, Int] = IO.syncException(intBuffer.get())

  def get(i: Int): IO[Exception, Int] = IO.syncException(intBuffer.get(i))

  def put(element: Int): IO[Exception, IntBuffer] =
    IO.syncException(intBuffer.put(element)).map(new IntBuffer(_))

  def put(index: Int, element: Int): IO[Exception, IntBuffer] =
    IO.syncException(intBuffer.put(index, element)).map(new IntBuffer(_))

  def asReadOnlyBuffer: IO[Exception, IntBuffer] =
    IO.syncException(intBuffer.asReadOnlyBuffer()).map(new IntBuffer(_))
}
