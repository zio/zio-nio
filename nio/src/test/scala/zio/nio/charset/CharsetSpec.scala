package zio
package nio
package charset

import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.{Spec, _}

import java.nio.charset.{CharacterCodingException, MalformedInputException, UnmappableCharacterException}

object CharsetSpec extends ZIOSpecDefault {

  override def spec: Spec[Any, CharacterCodingException] =
    suite("CharsetSpec")(
      chunkEncodeDecode(Charset.Standard.utf8),
      chunkEncodeDecode(Charset.Standard.utf16),
      bufferEncodeDecode(Charset.Standard.utf8),
      bufferEncodeDecode(Charset.Standard.utf16),
      test("utf8 encode") {
        Charset.Standard.utf8.encodeChunk(arabicChunk).map {
          assert(_)(equalTo(arabicUtf8))
        }
      },
      streamEncodeDecode(Charset.Standard.utf8),
      streamEncodeDecode(Charset.Standard.utf16Be),
      test("stream decode across chunk boundaries") {
        val byteStream = ZStream.fromChunks(arabicUtf8.map(Chunk.single): _*)
        for {
          chars <- byteStream.via(Charset.Standard.utf8.newDecoder.transducer()).runCollect
        } yield assert(chars)(equalTo(arabicChunk))
      },
      test("minimum buffer size for encoding") {
        val in = ZStream.fromChunk(arabicChunk)
        val t  = Charset.Standard.utf8.newEncoder.transducer(49)
        assertZIO(in.via(t).runDrain.exit)(dies(isSubtype[IllegalArgumentException](anything)))
      },
      test("minimum buffer size for decoding") {
        val in = ZStream.fromChunk(arabicUtf8)
        val t  = Charset.Standard.utf8.newDecoder.transducer(49)
        assertZIO(in.via(t).runDrain.exit)(dies(isSubtype[IllegalArgumentException](anything)))
      },
      test("handles encoding errors") {
        val in = ZStream.fromChunk(arabicChunk)
        val t  = Charset.Standard.iso8859_1.newEncoder.transducer()
        assertZIO(in.via(t).runDrain.exit)(fails(isSubtype[UnmappableCharacterException](anything)))
      },
      test("handles decoding errors") {
        val in = ZStream(0xd8, 0x00, 0xa5, 0xd8).map(_.toByte)
        val t  = Charset.Standard.utf16Le.newDecoder.transducer()
        assertZIO(in.via(t).runDrain.exit)(fails(isSubtype[MalformedInputException](anything)))
      }
    )

  val arabic = "إزَّي حضرتك؟"

  val arabicChunk: Chunk[Char] = Chunk.fromIterable(arabic)

  val arabicUtf8: Chunk[Byte] = Chunk(0xd8, 0xa5, 0xd8, 0xb2, 0xd9, 0x91, 0xd9, 0x8e, 0xd9, 0x8a, 0x20, 0xd8, 0xad,
    0xd8, 0xb6, 0xd8, 0xb1, 0xd8, 0xaa, 0xd9, 0x83, 0xd8, 0x9f)
    .map(_.toByte)

  def chunkEncodeDecode(charset: Charset): Spec[Any, Nothing] =
    test(s"chunk encode/decode ${charset.displayName}") {
      for {
        encoded <- charset.encodeChunk(arabicChunk)
        decoded <- charset.decodeChunk(encoded)
      } yield assert(decoded)(equalTo(arabicChunk))
    }

  def bufferEncodeDecode(charset: Charset): Spec[Any, Nothing] =
    test(s"buffer encode/decode ${charset.displayName}") {
      for {
        chars             <- Buffer.char(100)
        _                 <- chars.putChunk(arabicChunk)
        _                 <- chars.flip
        bytes             <- charset.encode(chars)
        charsHasRemaining <- chars.hasRemaining
        decoded           <- charset.decode(bytes)
        chunk             <- decoded.getChunk()
      } yield assert(charsHasRemaining)(isFalse) && assert(chunk)(equalTo(arabicChunk))
    }

  def streamEncodeDecode(charset: Charset): Spec[Any, CharacterCodingException] =
    test(s"stream encode/decode ${charset.displayName}") {
      val charStream = ZStream.fromIterable(arabic)
      for {
        byteChunks <- charStream.via(charset.newEncoder.transducer()).runCollect
        byteStream  = ZStream.fromIterable(byteChunks)
        chars      <- byteStream.via(charset.newDecoder.transducer()).runCollect
      } yield assert(chars)(equalTo(arabicChunk))
    }
}
