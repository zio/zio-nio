---
id: essentials_files
title:  "File Channel"
---

A `AsynchronousFileChannel` provides API for handling files in non-blocking way.

Required imports for presented snippets:

```scala mdoc:silent
import zio._
import zio.nio.core.channels._
import zio.nio.core.file._
import zio.console._
```

## Basic operations 

### Managed Resources

Any resource that supports a close or release effect implements the `IOCloseable` trait and can produce a `ZManaged` value by calling the `toIOManaged` method. `ZManaged` makes sure that the channel gets closed afterwards:

```scala mdoc:silent
val path = Path("file.txt")
val channelM = AsynchronousFileChannel.open(path).toManagedNio.use { channel => 
  readWriteOp(channel) *> lockOp(channel)
}
```

It is also possible to directly bracket calls where the power of `ZManaged` isn't required:

```scala mdoc:silent
val channelBracket = AsynchronousFileChannel.open(path).bracketNio { channel => 
  readWriteOp(channel) *> lockOp(channel)
}
```

More low-level code can forgo use of `ZManaged` or bracketing and manually arrange to call `close`, but note this requires great care to get right.

### Reading/Writing

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

### File Locks

File locking is also available as effect values with resource management, shown here with both the `ZManaged` and bracket styles:

```scala mdoc:silent
val lockOp = (channel: AsynchronousFileChannel) =>
  for {
    isShared     <- channel.lock().bracketNio(l => IO.succeed(l.isShared))
    _            <- putStrLn(isShared.toString)                                      // false

    managed      = channel.lock(position = 0, size = 10, shared = false).toManagedNio
    isOverlaping <- managed.use(l => IO.succeed(l.overlaps(5, 20)))
    _            <- putStrLn(isOverlaping.toString)                                  // true
  } yield ()
```
