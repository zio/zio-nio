package scalaz.nio

import scalaz.zio.IO

import java.nio.{ ByteOrder, ByteBuffer => JByteBuffer }

private[this] class ByteBuffer(val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {

  override def array: IO[Exception, Array[Byte]] =
    IO.syncException(byteBuffer.array())

  def order: IO[Nothing, ByteOrder] =
    IO.succeed(byteBuffer.order())

  def slice: IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.slice()).map(new ByteBuffer(_))

  override def get: IO[Exception, Byte] =
    IO.syncException(byteBuffer.get())

  override def get(i: Int): IO[Exception, Byte] =
    IO.syncException(byteBuffer.get(i))

  override def put(element: Byte): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.put(element)).map(new ByteBuffer(_))

  override def put(index: Int, element: Byte): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.put(index, element)).map(new ByteBuffer(_))

  override def asReadOnlyBuffer: IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.asReadOnlyBuffer()).map(new ByteBuffer(_))

  def asCharBuffer: IO[Exception, CharBuffer] =
    IO.syncException(new CharBuffer(byteBuffer.asCharBuffer()))

  def asDoubleBuffer: IO[Exception, DoubleBuffer] =
    IO.syncException(new DoubleBuffer(byteBuffer.asDoubleBuffer()))

  def asFloatBuffer: IO[Exception, FloatBuffer] =
    IO.syncException(new FloatBuffer(byteBuffer.asFloatBuffer()))

  def asIntBuffer: IO[Exception, IntBuffer] =
    IO.syncException(new IntBuffer(byteBuffer.asIntBuffer()))

  def asLongBuffer: IO[Exception, LongBuffer] =
    IO.syncException(new LongBuffer(byteBuffer.asLongBuffer()))

  def asShortBuffer: IO[Exception, ShortBuffer] =
    IO.syncException(new ShortBuffer(byteBuffer.asShortBuffer()))

  def putChar(value: Char): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putChar(value)).map(new ByteBuffer(_))

  def putChar(index: Int, value: Char): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putChar(index, value)).map(new ByteBuffer(_))

  def putDouble(value: Double): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putDouble(value)).map(new ByteBuffer(_))

  def putDouble(index: Int, value: Double): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putDouble(index, value)).map(new ByteBuffer(_))

  def putFloat(value: Float): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putFloat(value)).map(new ByteBuffer(_))

  def putFloat(index: Int, value: Float): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putFloat(index, value)).map(new ByteBuffer(_))

  def putInt(value: Int): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putInt(value)).map(new ByteBuffer(_))

  def putInt(index: Int, value: Int): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putInt(index, value)).map(new ByteBuffer(_))

  def putLong(value: Long): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putLong(value)).map(new ByteBuffer(_))

  def putLong(index: Int, value: Long): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putLong(index, value)).map(new ByteBuffer(_))

  def putShort(value: Short): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putShort(value)).map(new ByteBuffer(_))

  def putShort(index: Int, value: Short): IO[Exception, ByteBuffer] =
    IO.syncException(byteBuffer.putShort(index, value)).map(new ByteBuffer(_))

  def getChar(): IO[Exception, Char] =
    IO.syncException(byteBuffer.getChar())

  def getChar(index: Int): IO[Exception, Char] =
    IO.syncException(byteBuffer.getChar(index))

  def getDouble(): IO[Exception, Double] =
    IO.syncException(byteBuffer.getDouble())

  def getDouble(index: Int): IO[Exception, Double] =
    IO.syncException(byteBuffer.getDouble(index))

  def getFloat(): IO[Exception, Float] =
    IO.syncException(byteBuffer.getFloat())

  def getFloat(index: Int): IO[Exception, Float] =
    IO.syncException(byteBuffer.getFloat(index))

  def getInt(): IO[Exception, Int] =
    IO.syncException(byteBuffer.getInt())

  def getInt(index: Int): IO[Exception, Int] =
    IO.syncException(byteBuffer.getInt(index))

  def getLong(): IO[Exception, Long] =
    IO.syncException(byteBuffer.getLong())

  def getLong(index: Int): IO[Exception, Long] =
    IO.syncException(byteBuffer.getLong(index))

  def getShort(): IO[Exception, Short] =
    IO.syncException(byteBuffer.getShort())

  def getShort(index: Int): IO[Exception, Short] =
    IO.syncException(byteBuffer.getShort(index))
}
