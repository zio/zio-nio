---
id: end-of-stream-handling
title: "End of Stream Handling"
---

When reading from channels, the end of the stream may be reached at any time. This is indicated by the read effect failing with an `java.io.EOFException`. If you would prefer to explicitly represent the end-of-stream condition in the error channel, use the `eofCheck` extension method:

```scala mdoc:silent
import zio._
import zio.blocking.Blocking
import zio.nio._
import zio.nio.channels._
import zio.nio.file.Path
import java.io.IOException

val read100: ZIO[Blocking, Option[IOException], Chunk[Byte]] =
  FileChannel.open(Path("foo.txt"))
    .useNioBlockingOps(_.readChunk(100))
    .eofCheck
```

End-of-stream will be signalled with `None`. Any errors will be wrapped in `Some`.
