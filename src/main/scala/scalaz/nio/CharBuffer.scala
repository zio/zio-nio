package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, CharBuffer => JCharBuffer }

class CharBuffer private (private[nio] val javaBuffer: JCharBuffer)
    extends Buffer[Char, JCharBuffer](javaBuffer) {

  type Self = CharBuffer

  def array: IO[Exception, Array[Char]] = IO.syncException(javaBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.now(javaBuffer.order())

  def slice: IO[Exception, CharBuffer] = IO.syncException(javaBuffer.slice()).map(new CharBuffer(_))

  def get: IO[Exception, Char] = IO.syncException(javaBuffer.get())

  def get(i: Int): IO[Exception, Char] = IO.syncException(javaBuffer.get(i))

  def put(element: Char): IO[Exception, CharBuffer] =
    IO.syncException(javaBuffer.put(element)).map(new CharBuffer(_))

  def put(index: Int, element: Char): IO[Exception, CharBuffer] =
    IO.syncException(javaBuffer.put(index, element)).map(new CharBuffer(_))

  def asReadOnlyBuffer: IO[Exception, CharBuffer] =
    IO.syncException(javaBuffer.asReadOnlyBuffer()).map(new CharBuffer(_))
}

object CharBuffer extends BufferOps[Char, JCharBuffer, CharBuffer] {

  private[nio] def apply(javaBuffer: JCharBuffer): CharBuffer = new CharBuffer(javaBuffer)

  def allocate(capacity: Int): IO[Exception, CharBuffer] =
    IO.syncException(JCharBuffer.allocate(capacity)).map(new CharBuffer(_))

  def wrap(array: Array[Char]): IO[Exception, CharBuffer] =
    IO.syncException(JCharBuffer.wrap(array)).map(new CharBuffer(_))

  def wrap(array: Array[Char], offset: Int, length: Int): IO[Exception, CharBuffer] =
    IO.syncException(JCharBuffer.wrap(array, offset, length)).map(new CharBuffer(_))
}
