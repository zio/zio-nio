---
id: essentials_files
title:  "File Channel"
---

An `AsynchronousFileChannel` provides an API for handling files in a non-blocking way.

Required imports for presented snippets:

```scala mdoc:silent
import zio._
import zio.nio.channels._
import zio.nio.core.file._
import zio.console._
```

## Basic operations 

Opening a file for a given path (with no additional open attributes) returns a `ZManaged` instance on which we're running the intended operations. `ZManaged` makes sure that the channel gets closed afterwards:

```scala mdoc:silent
import java.nio.file.StandardOpenOption

val path = Path("file.txt")
val openOps = List(StandardOpenOption.READ, StandardOpenOption.WRITE)
val channelM = AsynchronousFileChannel.open(path, openOps: _*).use { channel =>
  readWriteOp(channel) *> lockOp(channel)
}
```

Reading and writing is performed as effects where raw `Byte` content is wrapped in `Chunk`:

```scala mdoc:silent
val readWriteOp = (channel: AsynchronousFileChannel) =>
  for {
    chunk <- channel.readChunk(20, 0L)
    text  = chunk.map(_.toChar).mkString
    _     <- putStrLn(text)
  
    input = Chunk.fromArray("message".toArray.map(_.toByte))
    _     <- channel.writeChunk(input, 0L)
  } yield ()
```

Contrary to previous operations, file locks are performed with the core `java.nio.channels.FileLock` class so
they are not in effects. Apart from basic acquire/release actions, the core API offers, among other things, partial locks and overlap checks:

```scala mdoc:silent
val lockOp = (channel: AsynchronousFileChannel) =>
  for {
    isShared     <- channel.lock().bracket(_.release.ignore)(l => IO.succeed(l.isShared))
    _            <- putStrLn(isShared.toString)                                      // false

    managed      = Managed.make(channel.lock(position = 0, size = 10, shared = false))(_.release.ignore)
    isOverlaping <- managed.use(l => IO.succeed(l.overlaps(5, 20)))
    _            <- putStrLn(isOverlaping.toString)                                  // true
  } yield ()
```

Also it's worth mentioning that we are treating `FileLock` as a resource here. 
For demonstration purposes we handled it in two different ways: using `bracket` and creating `Managed` for this.
