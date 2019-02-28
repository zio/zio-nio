package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, ShortBuffer => JShortBuffer }

private[nio] class ShortBuffer(val shortBuffer: JShortBuffer) extends Buffer[Short](shortBuffer) {

  override def array: IO[Exception, Array[Short]] = IO.syncException(shortBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.succeed(shortBuffer.order())

  def slice: IO[Exception, ShortBuffer] =
    IO.syncException(shortBuffer.slice()).map(new ShortBuffer(_))

  def get: IO[Exception, Short] = IO.syncException(shortBuffer.get())

  def get(i: Int): IO[Exception, Short] = IO.syncException(shortBuffer.get(i))

  def put(element: Short): IO[Exception, ShortBuffer] =
    IO.syncException(shortBuffer.put(element)).map(new ShortBuffer(_))

  def put(index: Int, element: Short): IO[Exception, ShortBuffer] =
    IO.syncException(shortBuffer.put(index, element)).map(new ShortBuffer(_))

  def asReadOnlyBuffer: IO[Exception, ShortBuffer] =
    IO.syncException(shortBuffer.asReadOnlyBuffer()).map(new ShortBuffer(_))
}
