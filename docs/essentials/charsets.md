---
id: essentials_charsets
title:  "Character Sets"
---

The `zio.nio.charset` package offers an API for ZIO programs to work with character sets, using the Java NIO support for character sets. Any character set supported by your JVM can be used.

## `Charset`

This class wraps the `java.nio.charset.Charset` class with a ZIO-friendly API. There are convenience methods for:

* encoding/decoding buffers
* encoding/decoding single chunks
* encoding/decoding strings

For more sophisticated encoding/decoding needs, a `CharsetEncoder` or `CharsetDecoder` can be obtained from a `Charset`.

### Standard Charsets

The standard set of charsets provided by Java are available in `Charset.Standard`.

* utf8
* utf16
* utf16Be
* utf16Le
* usAscii
* iso8859_1

JVMs typically support many more charsets than these; use `Charset.availableCharsets` to retrieve the complete list.

### Example

```scala mdoc:silent
import zio.nio.charset._
import zio.nio.file.Files
import zio.nio.file.Path

val s = "Hello, world!"
for {
  utf16Bytes <- Charset.Standard.utf16.encodeString(s)
  _          <- Files.writeBytes(Path("utf16.txt"), utf16Bytes)
} yield ()
``` 

## Stream Encoding and Decoding

Using streams instead of buffers or chunks is great for bigger jobs. ZIO Streams comes with a UTF-8 decoder built in, but if you need other character sets, or you need encoding, then ZIO-NIO can help—as long as you're running on the JVM.

Stream-based encoding and decoding are provided by the `transducer` method of the `CharsetEncoder` and `CharsetDecoder` classes respectively.

```scala mdoc:silent
import zio.nio.charset._
import zio.nio.channels.FileChannel
import zio.nio.file.Path
import zio.stream.ZStream
import zio.blocking.Blocking
import zio.console
import zio.ZIO

// dump a file encoded in ISO8859 to the console

FileChannel.open(Path("iso8859.txt")).use { fileChan =>
  val inStream: ZStream[Blocking, Exception, Byte] = ZStream.repeatEffectChunkOption {
    fileOps.readChunk(1000).asSomeError.flatMap { chunk =>
      if (chunk.isEmpty) ZIO.fail(None) else ZIO.succeed(chunk)
    }
  }

  // apply decoding transducer
  val charStream: ZStream[Blocking, Exception, Char] =
    inStream.transduce(Charset.Standard.iso8859_1.newDecoder.transducer())

  console.putStrLn("ISO8859 file contents:") *>
    charStream.foreachChunk(chars => console.putStr(chars.mkString))
}
``` 
