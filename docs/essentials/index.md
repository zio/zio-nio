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

## References

 - [ZIO github page](http://github.com/zio/zio)
 - [Java NIO docs](https://docs.oracle.com/javase/8/docs/api/java/nio/package-summary.html)
 - [Java NIO wikipedia](https://en.wikipedia.org/wiki/Non-blocking_I/O_(Java))
