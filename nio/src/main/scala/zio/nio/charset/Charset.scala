package zio
package nio
package charset

import com.github.ghik.silencer.silent

import java.nio.charset.IllegalCharsetNameException
import java.nio.{charset => j}
import java.{util => ju}
import scala.collection.JavaConverters._

final class Charset private (val javaCharset: j.Charset) extends Ordered[Charset] {

  @silent("object JavaConverters in package collection is deprecated")
  def aliases: Set[String] = javaCharset.aliases().asScala.toSet

  def canEncode: Boolean = javaCharset.canEncode

  override def compare(that: Charset): Int = javaCharset.compareTo(that.javaCharset)

  def contains(cs: Charset): Boolean = javaCharset.contains(cs.javaCharset)

  def decode(byteBuffer: ByteBuffer)(implicit trace: Trace): UIO[CharBuffer] =
    byteBuffer.withJavaBuffer(jBuf => ZIO.succeed(Buffer.charFromJava(javaCharset.decode(jBuf))))

  def displayName: String = javaCharset.displayName()

  def displayName(locale: ju.Locale): String = javaCharset.displayName(locale)

  def encode(charBuffer: CharBuffer)(implicit trace: Trace): UIO[ByteBuffer] =
    charBuffer.withJavaBuffer(jBuf => ZIO.succeed(Buffer.byteFromJava(javaCharset.encode(jBuf))))

  override def equals(other: Any): Boolean =
    other match {
      case cs: Charset => javaCharset.equals(cs.javaCharset)
      case _           => false
    }

  override def hashCode: Int = javaCharset.hashCode()

  def isRegistered: Boolean = javaCharset.isRegistered

  def name: String = javaCharset.name()

  def newDecoder: CharsetDecoder = CharsetDecoder.fromJava(javaCharset.newDecoder())

  def newEncoder: CharsetEncoder = CharsetEncoder.fromJava(javaCharset.newEncoder())

  override def toString: String = javaCharset.toString

  def encodeChunk(chunk: Chunk[Char])(implicit trace: Trace): UIO[Chunk[Byte]] =
    for {
      charBuf <- Buffer.char(chunk)
      byteBuf <- encode(charBuf)
      chunk   <- byteBuf.getChunk()
    } yield chunk

  def encodeString(s: CharSequence)(implicit trace: Trace): UIO[Chunk[Byte]] =
    for {
      charBuf <- Buffer.char(s)
      byteBuf <- encode(charBuf)
      chunk   <- byteBuf.getChunk()
    } yield chunk

  def decodeChunk(chunk: Chunk[Byte])(implicit trace: Trace): UIO[Chunk[Char]] =
    for {
      byteBuf <- Buffer.byte(chunk)
      charBuf <- decode(byteBuf)
      chunk   <- charBuf.getChunk()
    } yield chunk

  def decodeString(chunk: Chunk[Byte])(implicit trace: Trace): UIO[String] =
    for {
      byteBuf <- Buffer.byte(chunk)
      charBuf <- decode(byteBuf)
      s       <- charBuf.getString
    } yield s

}

object Charset {

  def fromJava(javaCharset: j.Charset): Charset = new Charset(javaCharset)

  @silent("deprecated")
  val availableCharsets: Map[String, Charset] =
    j.Charset.availableCharsets().asScala.mapValues(new Charset(_)).toMap

  val defaultCharset: Charset = fromJava(j.Charset.defaultCharset())

  /**
   * Returns a charset object for the named charset.
   *
   * @throws java.nio.charset.IllegalCharsetNameException
   *   if the given charset name is illegal
   * @throws java.nio.charset.UnsupportedCharsetException
   *   if no support for the named charset is available in this instance of the Java virtual machine
   */
  def forName(name: String): Charset = fromJava(j.Charset.forName(name))

  /**
   * Tells whether the named charset is supported.
   *
   * @throws java.nio.charset.IllegalCharsetNameException
   *   if the given charset name is illegal
   */
  def isSupported(name: String): Boolean = j.Charset.isSupported(name)

  /**
   * Tells whether the name is a legal charset name, ans is support by the JVM.
   */
  def isLegalAndSupported(name: String): Boolean =
    util.control.Exception.failAsValue(classOf[IllegalCharsetNameException])(false)(isSupported(name))

  /**
   * Returns a charset for the given name, if it is a legal name supported by the JVM.
   *
   * @return
   *   The charset if it is supported, otherwise `None`
   */
  def forNameIfSupported(name: String): Option[Charset] = if (isLegalAndSupported(name)) Some(forName(name)) else None

  object Standard {

    import j.StandardCharsets._

    val utf8: Charset = Charset.fromJava(UTF_8)

    val utf16: Charset = Charset.fromJava(UTF_16)

    val utf16Be: Charset = Charset.fromJava(UTF_16BE)

    val utf16Le: Charset = Charset.fromJava(UTF_16LE)

    val usAscii: Charset = Charset.fromJava(US_ASCII)

    val iso8859_1: Charset = Charset.fromJava(ISO_8859_1)

  }

}
