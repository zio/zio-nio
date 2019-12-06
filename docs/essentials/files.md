---
id: essentials_files
title:  "File Channel"
---

A `AsynchronousFileChannel` provides API for handling files in non-blocking way.

Required imports for presented snippets:

```scala mdoc:silent
import zio._
import zio.nio.channels._
import zio.nio.core.file._
import zio.console._
```

## Basic operations 

Opening file for given path (with no additional open attributes) returns a `ZManaged` instance on which we're running the intended operations. `ZManaged` makes sure that the channel gets closed afterwards:

```scala mdoc:silent
val path = Path("file.txt")
val channelM = AsynchronousFileChannel.open(path).use { channel => 
  readWriteOp(channel) *> lockOp(channel)
}
```

Reading and writing is performed as effects where raw `Byte` content is wrapped in `Chunk`:

```scala mdoc:silent
val readWriteOp = (channel: AsynchronousFileChannel) =>
  for {
    chunk <- channel.read(20, 0)
    text  = chunk.map(_.toChar).mkString
    _     <- putStrLn(text)
  
    input = Chunk.fromArray("message".toArray.map(_.toByte))
    _     <- channel.write(input, 0)
  } yield ()
```

Contrary to previous operations file locks are performed with core `java.nio.channels.FileLock` class so
it's not in an effect. Apart from basic acquire/release actions Core API offers e.g. partial locks and overlaps checks:

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

Also it's worth mentioning that we are treating here `FileLock` as a resource. 
For demonstration purposes we handled it in two different ways: using `bracket` and creating `Managed` for this.
