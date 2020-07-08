---
id: essentials_index
title:  "Overview"
---

ZIO-NIO is a ZIO wrapper on Java NIO. Is a small and unopionanted ZIO interface to NIO that just wraps NIO API in ZIO effects. Some useful higher level abstractions are provided on top of the unopinionated core.

## Installation

`ZIO-NIO` is available via maven repo. Add this to your dependencies in `sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-nio-core" % "1.0.0-RC6"
```
or
```scala
libraryDependencies += "dev.zio" %% "zio-nio" % "1.0.0-RC6"
```

## Resource Management

The NIO API offers several abstractions which consume OS resources until closed, for example file channels. This is modelled in ZIO-NIO via the `IOCloseable` trait, which is extended by all abstractions that must be closed.

The `IOCloseable#close` method can be used to manually close resources, however this can be surprisingly tricky to use correctly in ZIO-based code. This is why ZIO has resource management capabilities built in, and ZIO-NIO offers versions of these tailored to the NIO API. The two "styles" of resource management are *bracketing* and *ZManaged*.

### Bracketing

Bracketing is essentially a functional programming equivalent to Java's `try`/`finally` or "try with resources". Any ZIO effect value that produces a closeable NIO resource can be used in a bracketed fashion via the `.bracketNio` method:

```scala
import zio.nio.core._
import zio.nio.core.channels.FileChannel

FileChannel.open(path).bracketNio { fileChannel =>
  // fileChannel can be used here
}
```

### ZManaged

ZIO's `ZManaged` type represents a managed resource. `ZManaged` values can be composed together to acquire and release multiple resources in a reliable way. Any ZIO effect value that produces a closeable NIO resource can be lifted into a `ZManaged` value via the `.toIOManaged` method. An example of acquiring, using and releasing multiple resources:

```scala
val managed = FileChannel.open(path).toIOManaged <*>
  SocketChannel.open(address).toIOManaged

managed.use {
  case (fileChannel, socketChannel) => // use the channels
}
```

## Main abstractions

 - **[File Channel](files.md)** — For processing files that are available locally. For every operation a new fiber is started to perform operation
 - **[Socket Channel](sockets.md)** — Provides API for remote communication with `InetSocket`s 

## References

 - [ZIO github page](http://github.com/zio/zio)
 - [Java NIO docs](https://docs.oracle.com/javase/8/docs/api/java/nio/package-summary.html)
 - [Java NIO wikipedia](https://en.wikipedia.org/wiki/Non-blocking_I/O_(Java))
