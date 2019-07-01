package zio.nio

import zio.IO

import java.nio.{ ByteOrder, ByteBuffer => JByteBuffer }

private[this] class ByteBuffer(val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {

  override val array: IO[Exception, Array[Byte]] =
    IO.effect(byteBuffer.array()).refineToOrDie[Exception]

  val order: IO[Nothing, ByteOrder] =
    IO.succeed(byteBuffer.order())

  val slice: IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.slice()).map(new ByteBuffer(_)).refineToOrDie[Exception]

  override val get: IO[Exception, Byte] =
    IO.effect(byteBuffer.get()).refineToOrDie[Exception]

  override def get(i: Int): IO[Exception, Byte] =
    IO.effect(byteBuffer.get(i)).refineToOrDie[Exception]

  override def put(element: Byte): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.put(element)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  override def put(index: Int, element: Byte): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.put(index, element)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  override val asReadOnlyBuffer: IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.asReadOnlyBuffer()).map(new ByteBuffer(_)).refineToOrDie[Exception]

  val asCharBuffer: IO[Exception, CharBuffer] =
    IO.effect(new CharBuffer(byteBuffer.asCharBuffer())).refineToOrDie[Exception]

  val asDoubleBuffer: IO[Exception, DoubleBuffer] =
    IO.effect(new DoubleBuffer(byteBuffer.asDoubleBuffer())).refineToOrDie[Exception]

  val asFloatBuffer: IO[Exception, FloatBuffer] =
    IO.effect(new FloatBuffer(byteBuffer.asFloatBuffer())).refineToOrDie[Exception]

  val asIntBuffer: IO[Exception, IntBuffer] =
    IO.effect(new IntBuffer(byteBuffer.asIntBuffer())).refineToOrDie[Exception]

  val asLongBuffer: IO[Exception, LongBuffer] =
    IO.effect(new LongBuffer(byteBuffer.asLongBuffer())).refineToOrDie[Exception]

  val asShortBuffer: IO[Exception, ShortBuffer] =
    IO.effect(new ShortBuffer(byteBuffer.asShortBuffer())).refineToOrDie[Exception]

  def putChar(value: Char): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putChar(value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putChar(index: Int, value: Char): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putChar(index, value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putDouble(value: Double): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putDouble(value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putDouble(index: Int, value: Double): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putDouble(index, value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putFloat(value: Float): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putFloat(value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putFloat(index: Int, value: Float): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putFloat(index, value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putInt(value: Int): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putInt(value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putInt(index: Int, value: Int): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putInt(index, value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putLong(value: Long): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putLong(value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putLong(index: Int, value: Long): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putLong(index, value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putShort(value: Short): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putShort(value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  def putShort(index: Int, value: Short): IO[Exception, ByteBuffer] =
    IO.effect(byteBuffer.putShort(index, value)).map(new ByteBuffer(_)).refineToOrDie[Exception]

  val getChar: IO[Exception, Char] =
    IO.effect(byteBuffer.getChar()).refineToOrDie[Exception]

  def getChar(index: Int): IO[Exception, Char] =
    IO.effect(byteBuffer.getChar(index)).refineToOrDie[Exception]

  val getDouble: IO[Exception, Double] =
    IO.effect(byteBuffer.getDouble()).refineToOrDie[Exception]

  def getDouble(index: Int): IO[Exception, Double] =
    IO.effect(byteBuffer.getDouble(index)).refineToOrDie[Exception]

  val getFloat: IO[Exception, Float] =
    IO.effect(byteBuffer.getFloat()).refineToOrDie[Exception]

  def getFloat(index: Int): IO[Exception, Float] =
    IO.effect(byteBuffer.getFloat(index)).refineToOrDie[Exception]

  val getInt: IO[Exception, Int] =
    IO.effect(byteBuffer.getInt()).refineToOrDie[Exception]

  def getInt(index: Int): IO[Exception, Int] =
    IO.effect(byteBuffer.getInt(index)).refineToOrDie[Exception]

  val getLong: IO[Exception, Long] =
    IO.effect(byteBuffer.getLong()).refineToOrDie[Exception]

  def getLong(index: Int): IO[Exception, Long] =
    IO.effect(byteBuffer.getLong(index)).refineToOrDie[Exception]

  val getShort: IO[Exception, Short] =
    IO.effect(byteBuffer.getShort()).refineToOrDie[Exception]

  def getShort(index: Int): IO[Exception, Short] =
    IO.effect(byteBuffer.getShort(index)).refineToOrDie[Exception]
}
