---
id: essentials_files
title:  "File Channel"
---

A `AsynchronousFileChannel` provides API for handling files in non-blocking way.

Required imports for presented snippets:

```scala mdoc:silent
import java.nio.file.Paths
import zio._
import zio.nio.channels._
import zio.console._
```

## Basic operations 

Opening file for given path and no additional open attributes:

```scala mdoc:silent
val channelM = for {
  path <- ZIO.effectTotal(Paths.get("./file.txt"))
  channel <- AsynchronousFileChannel.open(path, Set.empty)
} yield channel
```

Reading and writing is performed as effects where raw `Byte` content is wrapped in `Chunk`:

```scala mdoc:silent
for {
  channel <- channelM
  chunk <- channel.read(20, 0)
  text = chunk.map(_.toChar).mkString
  _ <- putStrLn(text)

  input = Chunk.fromArray("message".toArray.map(_.toByte))
  _ <- channel.write(input, 0)
} yield ()
```

Contrary to previous operations file locks are performed with core `java.nio.channels.FileLock` class so
it's not in an effect. Apart from basic acquire/release actions Core API offers e.g. partial locks and overlaps checks:

```scala mdoc:silent
for {
  channel <- channelM

  isShared <- channel.lock.bracket(l => IO.effectTotal(l.release()))(l => IO.effectTotal(l.isShared))
  _ <- putStrLn(isShared.toString)                                      // false

  managed = Managed.make(channel.lock(position = 0, size = 10, shared = false))(l => IO.effectTotal(l.release()))
  isOverlaping <- managed.use(l => IO.effectTotal(l.overlaps(5, 20)))   // true

} yield ()
```

Also it's worth mentioning that we are treating here `FileLock` as a resource. 
For demonstration purposes we handled it in two different ways: using `bracket` and creating `Managed` for this.
