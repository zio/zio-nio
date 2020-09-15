---
id: essentials_index
title:  "Overview"
---

ZIO-NIO is a ZIO wrapper on Java NIO. It comes in two flavours:

 - `zio.nio.core` - a small and unopionanted ZIO interface to NIO that just wraps NIO API in ZIO effects,
 - `zio.nio` - an opinionated interface with deeper ZIO integration that provides more type and resource safety.

A very simple example of these differences would be the signature of `apply` method on `AsynchronousSocketChannel`:
```scala
//zio.nio.core
def apply(): IO[Exception, AsynchronousSocketChannel]
```
vs
```scala
//zio.nio
def apply(): Managed[Exception, AsynchronousSocketChannel]
```

## Installation

`ZIO-NIO` is available via maven repo. Add this to your dependencies in `sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-nio-core" % "1.0.0-RC9"
```
or
```scala
libraryDependencies += "dev.zio" %% "zio-nio" % "1.0.0-RC9"
```

## Main abstractions

 - **[File Channel](files.md)** — For processing files that are available locally. For every operation a new fiber is started to perform operation
 - **[Socket Channel](sockets.md)** — Provides API for remote communication with `InetSocket`s 
 - **[Character Sets](charsets.md)** - For encoding or decoding character data

### End-Of-Stream Handling

When reading from channels, the end of the stream may be reached at any time. This is indicated by the read effect failing with an `java.io.EOFException`. If you would prefer to explicitly represent the end-of-stream condition in the error channel, use the `eofCheck` extension method:

```scala mdoc:silent
import zio._
import zio.blocking.Blocking
import zio.nio.core._
import zio.nio.core.channels._
import zio.nio.core.file.Path
import java.io.IOException

val read100: ZIO[Blocking, Option[IOException], Chunk[Byte]] =
  FileChannel.open(Path("foo.txt"))
    .asSomeError
    .flatMap(_.readChunk(100).eofCheck)
```

If the error is `None` if end-of-stream is reached, and it is `Some` if the read failed.

## References

 - [ZIO github page](http://github.com/zio/zio)
 - [Java NIO docs](https://docs.oracle.com/javase/8/docs/api/java/nio/package-summary.html)
 - [Java NIO wikipedia](https://en.wikipedia.org/wiki/Non-blocking_I/O_(Java))
