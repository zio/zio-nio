package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, IntBuffer => JIntBuffer }

class IntBuffer private (private[nio] val javaBuffer: JIntBuffer)
    extends Buffer[Int, JIntBuffer](javaBuffer) {

  type Self = IntBuffer

  def array: IO[Exception, Array[Int]] = IO.syncException(javaBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.now(javaBuffer.order())

  def slice: IO[Exception, IntBuffer] = IO.syncException(javaBuffer.slice()).map(new IntBuffer(_))

  def get: IO[Exception, Int] = IO.syncException(javaBuffer.get())

  def get(i: Int): IO[Exception, Int] = IO.syncException(javaBuffer.get(i))

  def put(element: Int): IO[Exception, IntBuffer] =
    IO.syncException(javaBuffer.put(element)).map(new IntBuffer(_))

  def put(index: Int, element: Int): IO[Exception, IntBuffer] =
    IO.syncException(javaBuffer.put(index, element)).map(new IntBuffer(_))

  def asReadOnlyBuffer: IO[Exception, IntBuffer] =
    IO.syncException(javaBuffer.asReadOnlyBuffer()).map(new IntBuffer(_))
}

object IntBuffer extends BufferOps[Int, JIntBuffer, IntBuffer] {

  private[nio] def apply(javaBuffer: JIntBuffer): IntBuffer = new IntBuffer(javaBuffer)

  def allocate(capacity: Int): IO[Exception, IntBuffer] =
    IO.syncException(JIntBuffer.allocate(capacity)).map(new IntBuffer(_))

  def wrap(array: Array[Int]): IO[Exception, IntBuffer] =
    IO.syncException(JIntBuffer.wrap(array)).map(new IntBuffer(_))

  def wrap(array: Array[Int], offset: Int, length: Int): IO[Exception, IntBuffer] =
    IO.syncException(JIntBuffer.wrap(array, offset, length)).map(new IntBuffer(_))
}
