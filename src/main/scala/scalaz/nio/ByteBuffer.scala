package scalaz.nio

import scalaz.zio.{ IO, JustExceptions }

import java.nio.{ ByteOrder, ByteBuffer => JByteBuffer }

private[this] class ByteBuffer(val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {

  override def array: IO[Exception, Array[Byte]] =
    IO.effect(byteBuffer.array()).refineOrDie(JustExceptions)

  def order: IO[Nothing, ByteOrder] =
    IO.succeed(byteBuffer.order())

  def slice: IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.slice()).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  override def get: IO[Exception, Byte] =
    IO.effect(byteBuffer.get()).refineOrDie(JustExceptions)

  override def get(i: Int): IO[Exception, Byte] =
    IO.effect(byteBuffer.get(i)).refineOrDie(JustExceptions)

  override def put(element: Byte): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.put(element)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  override def put(index: Int, element: Byte): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.put(index, element)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  override def asReadOnlyBuffer: IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.asReadOnlyBuffer()).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def asCharBuffer: IO[Exception, CharBuffer] =
    IO.effect(new CharBuffer(byteBuffer.asCharBuffer())).refineOrDie(JustExceptions)

  def asDoubleBuffer: IO[Exception, DoubleBuffer] =
    IO.effect(new DoubleBuffer(byteBuffer.asDoubleBuffer())).refineOrDie(JustExceptions)

  def asFloatBuffer: IO[Exception, FloatBuffer] =
    IO.effect(new FloatBuffer(byteBuffer.asFloatBuffer())).refineOrDie(JustExceptions)

  def asIntBuffer: IO[Exception, IntBuffer] =
    IO.effect(new IntBuffer(byteBuffer.asIntBuffer())).refineOrDie(JustExceptions)

  def asLongBuffer: IO[Exception, LongBuffer] =
    IO.effect(new LongBuffer(byteBuffer.asLongBuffer())).refineOrDie(JustExceptions)

  def asShortBuffer: IO[Exception, ShortBuffer] =
    IO.effect(new ShortBuffer(byteBuffer.asShortBuffer())).refineOrDie(JustExceptions)

  def putChar(value: Char): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putChar(value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putChar(index: Int, value: Char): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putChar(index, value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putDouble(value: Double): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putDouble(value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putDouble(index: Int, value: Double): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putDouble(index, value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putFloat(value: Float): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putFloat(value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putFloat(index: Int, value: Float): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putFloat(index, value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putInt(value: Int): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putInt(value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putInt(index: Int, value: Int): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putInt(index, value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putLong(value: Long): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putLong(value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putLong(index: Int, value: Long): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putLong(index, value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putShort(value: Short): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putShort(value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def putShort(index: Int, value: Short): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putShort(index, value)).map(new ByteBuffer(_)).refineOrDie(JustExceptions)

  def getChar(): IO[Exception, Char] =
    IO.effect(byteBuffer.getChar()).refineOrDie(JustExceptions)

  def getChar(index: Int): IO[Exception, Char] =
    IO.effect(byteBuffer.getChar(index)).refineOrDie(JustExceptions)

  def getDouble(): IO[Exception, Double] =
    IO.effect(byteBuffer.getDouble()).refineOrDie(JustExceptions)

  def getDouble(index: Int): IO[Exception, Double] =
    IO.effect(byteBuffer.getDouble(index)).refineOrDie(JustExceptions)

  def getFloat(): IO[Exception, Float] =
    IO.effect(byteBuffer.getFloat()).refineOrDie(JustExceptions)

  def getFloat(index: Int): IO[Exception, Float] =
    IO.effect(byteBuffer.getFloat(index)).refineOrDie(JustExceptions)

  def getInt(): IO[Exception, Int] =
    IO.effect(byteBuffer.getInt()).refineOrDie(JustExceptions)

  def getInt(index: Int): IO[Exception, Int] =
    IO.effect(byteBuffer.getInt(index)).refineOrDie(JustExceptions)

  def getLong(): IO[Exception, Long] =
    IO.effect(byteBuffer.getLong()).refineOrDie(JustExceptions)

  def getLong(index: Int): IO[Exception, Long] =
    IO.effect(byteBuffer.getLong(index)).refineOrDie(JustExceptions)

  def getShort(): IO[Exception, Short] =
    IO.effect(byteBuffer.getShort()).refineOrDie(JustExceptions)

  def getShort(index: Int): IO[Exception, Short] =
    IO.effect(byteBuffer.getShort(index)).refineOrDie(JustExceptions)
}
