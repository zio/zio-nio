package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, ByteBuffer => JByteBuffer }

class ByteBuffer private (private[nio] val javaBuffer: JByteBuffer)
    extends Buffer[Byte, JByteBuffer](javaBuffer) {

  type Self = ByteBuffer

  def array: IO[Exception, Array[Byte]] = IO.syncException(javaBuffer.array())

  def order: IO[Nothing, ByteOrder] = IO.now(javaBuffer.order())

  def slice: IO[Exception, ByteBuffer] = IO.syncException(javaBuffer.slice()).map(new ByteBuffer(_))

  def get: IO[Exception, Byte] = IO.syncException(javaBuffer.get())

  def get(i: Int): IO[Exception, Byte] = IO.syncException(javaBuffer.get(i))

  def put(element: Byte): IO[Exception, ByteBuffer] =
    IO.syncException(javaBuffer.put(element)).map(new ByteBuffer(_))

  def put(index: Int, element: Byte): IO[Exception, ByteBuffer] =
    IO.syncException(javaBuffer.put(index, element)).map(new ByteBuffer(_))

  def asReadOnlyBuffer: IO[Exception, ByteBuffer] =
    IO.syncException(javaBuffer.asReadOnlyBuffer()).map(new ByteBuffer(_))

  def asCharBuffer: IO[Exception, CharBuffer] =
    IO.syncException(CharBuffer(javaBuffer.asCharBuffer()))

  def asDoubleBuffer: IO[Exception, DoubleBuffer] =
    IO.syncException(DoubleBuffer(javaBuffer.asDoubleBuffer()))

  def asFloatBuffer: IO[Exception, FloatBuffer] =
    IO.syncException(FloatBuffer(javaBuffer.asFloatBuffer()))

  def asIntBuffer: IO[Exception, IntBuffer] = IO.syncException(IntBuffer(javaBuffer.asIntBuffer()))

  def asLongBuffer: IO[Exception, LongBuffer] =
    IO.syncException(LongBuffer(javaBuffer.asLongBuffer()))

  def asShortBuffer: IO[Exception, ShortBuffer] =
    IO.syncException(ShortBuffer(javaBuffer.asShortBuffer()))
}

object ByteBuffer extends BufferOps[Byte, JByteBuffer, ByteBuffer] {

  private[nio] def apply(javaBuffer: JByteBuffer): ByteBuffer = new ByteBuffer(javaBuffer)

  def allocate(capacity: Int): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.allocate(capacity)).map(new ByteBuffer(_))

  def wrap(array: Array[Byte]): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.wrap(array)).map(new ByteBuffer(_))

  def wrap(array: Array[Byte], offset: Int, length: Int): IO[Exception, ByteBuffer] =
    IO.syncException(JByteBuffer.wrap(array, offset, length)).map(new ByteBuffer(_))
}
