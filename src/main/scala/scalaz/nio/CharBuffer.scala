package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, CharBuffer => JCharBuffer }

private[nio] class CharBuffer(val charBuffer: JCharBuffer) extends Buffer[Char](charBuffer) {

  override def array: IO[Exception, Array[Char]] = IO.syncException(charBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.succeed(charBuffer.order())

  def slice: IO[Exception, CharBuffer] = IO.syncException(charBuffer.slice()).map(new CharBuffer(_))

  override def get: IO[Exception, Char] = IO.syncException(charBuffer.get())

  override def get(i: Int): IO[Exception, Char] = IO.syncException(charBuffer.get(i))

  override def put(element: Char): IO[Exception, CharBuffer] =
    IO.syncException(charBuffer.put(element)).map(new CharBuffer(_))

  override def put(index: Int, element: Char): IO[Exception, CharBuffer] =
    IO.syncException(charBuffer.put(index, element)).map(new CharBuffer(_))

  override def asReadOnlyBuffer: IO[Exception, CharBuffer] =
    IO.syncException(charBuffer.asReadOnlyBuffer()).map(new CharBuffer(_))
}
