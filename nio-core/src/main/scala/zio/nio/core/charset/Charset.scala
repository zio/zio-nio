package zio
package nio
package core
package charset

import java.nio.{ charset => j }
import java.{ util => ju }

import com.github.ghik.silencer.silent

import scala.collection.JavaConverters._

final class Charset private (val javaCharset: j.Charset) extends Ordered[Charset] {

  @silent("object JavaConverters in package collection is deprecated")
  def aliases: Set[String] = javaCharset.aliases().asScala.toSet

  def canEncode: Boolean = javaCharset.canEncode

  override def compare(that: Charset): Int =
    javaCharset.compareTo(that.javaCharset)

  def contains(cs: Charset): Boolean = javaCharset.contains(cs.javaCharset)

  def decode(byteBuffer: ByteBuffer): UIO[CharBuffer] =
    byteBuffer.withJavaBuffer(jBuf => UIO.effectTotal(Buffer.charFromJava(javaCharset.decode(jBuf))))

  def displayName: String = javaCharset.displayName()

  def displayName(locale: ju.Locale): String = javaCharset.displayName(locale)

  def encode(charBuffer: CharBuffer): UIO[ByteBuffer] =
    charBuffer.withJavaBuffer(jBuf => UIO.effectTotal(Buffer.byteFromJava(javaCharset.encode(jBuf))))

  override def equals(other: Any): Boolean = other match {
    case cs: Charset => javaCharset.equals(cs.javaCharset)
    case _           => false
  }

  override def hashCode: Int = javaCharset.hashCode()

  def isRegistered: Boolean = javaCharset.isRegistered

  def name: String = javaCharset.name()

  def newDecoder: CharsetDecoder =
    CharsetDecoder.fromJava(javaCharset.newDecoder())

  def newEncoder: CharsetEncoder = CharsetEncoder.fromJava(javaCharset.newEncoder())

  override def toString: String = javaCharset.toString

  def encodeChunk(chunk: Chunk[Char]): UIO[Chunk[Byte]] =
    for {
      charBuf <- Buffer.char(chunk)
      byteBuf <- encode(charBuf)
      chunk   <- byteBuf.getChunk().orDie
    } yield chunk

  def encodeString(s: String): UIO[Chunk[Byte]] =
    for {
      charBuf <- Buffer.char(s)
      byteBuf <- encode(charBuf)
      chunk   <- byteBuf.getChunk().orDie
    } yield chunk

  def decodeChunk(chunk: Chunk[Byte]): UIO[Chunk[Char]] =
    for {
      byteBuf <- Buffer.byte(chunk)
      charBuf <- decode(byteBuf)
      chunk   <- charBuf.getChunk().orDie
    } yield chunk

  def decodeString(chunk: Chunk[Byte]): UIO[String] =
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

  def forName(name: String): Charset = fromJava(j.Charset.forName(name))

  def isSupported(name: String): Boolean = j.Charset.isSupported(name)

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
