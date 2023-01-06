---
id: index
title: "Introduction to ZIO NIO"
sidebar_label: "ZIO NIO"
---

[ZIO NIO](https://zio.dev/zio-nio) is a ZIO wrapper on Java NIO, an opinionated interface with deep ZIO integration that provides type and resource safety.

@PROJECT_BADGES@

## Introduction

Java NIO is unsafe, and can surprise you a lot with e.g. hiding the actual error in IO operation and giving you only true/false values when IO was successful/not successful. ZIO NIO on the other hand embraces the full power of ZIO effects, environment, error and resource management to provide type-safe, performant, purely-functional, low-level, and unopinionated wrapping of Java NIO functionality.

In Java, there are two packages for I/O operations:

1. Java IO (`java.io`)
    - Standard Java IO API
    - Introduced since Java 1.0
    - Stream-based API
    - **Blocking I/O operation**

2. Java NIO (`java.nio`)
    - Introduced since Java 1.4
    - NIO means _New IO_, an alternative to the standard Java IO API
    - It can operate in a **non-blocking mode** if possible
    - Buffer-based API

The [Java NIO](https://docs.oracle.com/javase/8/docs/api/java/nio/package-summary.html) is an alternative to the Java IO API. Because it supports non-blocking IO, it can be more performant in concurrent environments like web services.

## Main Abstractions

- **[Using Blocking APIs](blocking.md)** — How to deal with NIO APIs that block the calling thread
- **[File Channel](files.md)** — For processing files that are available locally. For every operation a new fiber is started to perform the operation.
- **[Socket Channel](sockets.md)** — Provides an API for remote communication with `InetSocket`s.
- **[Resource Management](resources.md)** - Avoiding resource leaks
- **[Character Sets](charsets.md)** - For encoding or decoding character data.

## Installation

ZIO NIO comes in two flavors:

- **`zio.nio.core`** — a small and unopionanted ZIO interface to NIO that just wraps NIO API in ZIO effects,
- **`zio.nio`** — an opinionated interface with deeper ZIO integration that provides more type and resource safety.

In order to use this library, we need to add one of the following lines in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-nio-core" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-nio"      % "@VERSION@" 
```

## Example

Let's try writing a simple server using `zio-nio` module:

```scala
import zio._
import zio.console._
import zio.nio.channels._
import zio.nio.core._
import zio.stream._

object ZIONIOServerExample extends zio.App {
  val myApp =
    AsynchronousServerSocketChannel()
      .use(socket =>
        for {
          addr <- InetSocketAddress.hostName("localhost", 8080)
          _ <- socket.bindTo(addr)
          _ <- putStrLn(s"Waiting for incoming connections on $addr endpoint").orDie
          _ <- ZStream
            .repeatEffect(socket.accept.preallocate)
            .map(_.withEarlyRelease)
            .mapMPar(16) {
              _.use { case (closeConn, channel) =>
                for {
                  _ <- putStrLn("Received connection").orDie
                  data <- ZStream
                    .repeatEffectOption(
                      channel.readChunk(64).eofCheck.orElseFail(None)
                    )
                    .flattenChunks
                    .transduce(ZTransducer.utf8Decode)
                    .run(Sink.foldLeft("")(_ + _))
                  _ <- closeConn
                  _ <- putStrLn(s"Request Received:\n${data.mkString}").orDie
                } yield ()
              }
            }.runDrain
        } yield ()
      ).orDie
   
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```

Now we can send our requests to the server using _curl_ command:

```
curl -X POST localhost:8080 -d "Hello, ZIO NIO!"
```

## References

 - [ZIO github page](http://github.com/zio/zio)
 - [Java NIO docs](https://docs.oracle.com/javase/8/docs/api/java/nio/package-summary.html)
 - [Java NIO wikipedia](https://en.wikipedia.org/wiki/Non-blocking_I/O_(Java))

## Background

* [Scala IO](https://www.scala-lang.org/api/2.12.3/scala/io/index.html)
* [Http4s Blaze](https://github.com/http4s/blaze)
* [Ammonite](https://github.com/lihaoyi/Ammonite/)
* [Better Files](https://github.com/pathikrit/better-files)
* [Towards a safe, sane I O library in Scala](https://www.youtube.com/watch?feature=player_embedded&v=uaYKkpqs6CE)
* [Haskell NIO](https://wiki.haskell.org/NIO)
* [Non Blocking IO](https://www.youtube.com/watch?v=uKc0Gx_lPsg)
* [Blocking vs Non-blocking IO](http://tutorials.jenkov.com/java-nio/nio-vs-io.html)
