---
id: essentials_blocking
title:  "Blocking I/O"
---

The default ZIO runtime assumes that threads will never block, and maintains a small fixed-size thread pool to perform all its operations. If threads become blocked, CPU utilization can be reduced as the number of available threads drops below the number of available CPU cores. If enough threads block, the entire program may halt.

Another issue with blocked threads is interruption. It is important that if the ZIO fiber is interrupted that this cancels the blocking operation and unblocks the thread.

Many NIO operations can block the calling thread when called. ZIO-NIO provides APIs to help ZIO-based code deal with this. The following describes how to use channels that offer blocking operations, which is all channels except for the asynchronous ones.

## Blocking and Non-Blocking Channel Operations

Channel APIs that may block are not exposed on the channel itself. They are accessed via the channel's `flatMapBlocking` method. You provide this method a function that excepts a `BlockingOps` object and returns a `ZIO` effect value. The `BlockingOps` parameter will be appropriate to the type of channel and has the actual blocking I/O effects such as read and write.

The `flatMapBlocking` method performs some setup required for safe use of blocking NIO APIs:

* Puts the channel in blocking mode
* Runs the resulting effect value on ZIO's blocking thread pool, leaving the standard pool unblocked.
* Installs interrupt handling, so the channel will be closed if the ZIO fiber is interrupted. This unblocks the blocked I/O operation. (Note that NIO does not offer a way to interrupt a blocked I/O operation on a channel that does not close the channel).
 
Non-blocking usage does not require this special handling, but for consistency the non-blocking operations are accessed in a similar way by calling `flatMapNonBlocking` on the channel. For some channels there are some small differences between the blocking and non-blocking APIs. For example, `SocketChannel` only offers the `finishConnect` operation in the non-blocking case, as it is never needed in blocking mode.
 
 ```scala mdoc:silent
import zio.ZIO
import zio.nio._
import zio.nio.channels._
 
def readHeader(c: SocketChannel): ZIO[Blocking, IOException, (Chunk[Byte], Chunk[Byte])] =
  c.useBlocking { ops =>
    ops.readChunk(10) <*> ops.readChunk(25)
  }
 ```

### Using Channels

To help with the common use-case where you want to create a channel, there is versions of `flatMapBlocking` and `flatMapNonBlocking` that can be called directly on a ZIO value providing a channel.

`flatMapNioBlocking` provides both the channel and the requested type of operations:

```scala mdoc:silent
import zio.nio._
import zio.nio.channels._

SocketChannel.open.flatMapNioBlocking { (channel, blockingOps) => 
  blockingOps.readChunk(100) <*> channel.remoteAddress
}
```

If you don't need the channel, there's `flatMapNioBlockingOps`:

```scala mdoc:silent
import zio.nio.channels._

SocketChannel.open.flatMapNioBlockingOps { blockingOps => 
  blockingOps.readChunk(100)
}
```

To use the channel in non-blocking mode, there's corresponding `flatMapNioNonBlocking` and `flatMapNioNonBlockingOps` methods.

### Avoiding Asynchronous Boundaries

If you have a complex program that makes more than one call to `flatMapBlocking`, then it may be worth running *all* of the ZIO-NIO parts using the blocking pool. This can be done by wrapping the effect value with your ZIO-NIO operations in `zio.blocking.blocking`.

If this isn't done, you can end up with the calls using `BlockingOps` running on a thread from the blocking pool, while the other parts run on a thread from the standard pool. This involves an "asynchronous boundary" whever the fiber changes the underlying thread it's running on, which imposes some overheads including a full memory barrier. By using `zio.blocking.blocking` up-front, all the code can run on the same thread from the blocking pool.
 
## Comparing the Channel Options

There are three main styles of channel available: blocking, non-blocking and asynchronous. Which to choose?

### Blocking Channels

Easy to use, with a straight-forward operation. The downsides are that you have to use `flatMapBlocking`, which creates a new thread, and will create an additional thread for every forked fiber subsequently created. Essentially you have a blocked thread for every active I/O call, which limits scalability. Also, the additional interrupt handling logic imposes a small overhead.

### Non-Blocking Channels

These scale very well because you basically do as many concurrent I/O operations as you like without creating any new threads. The big downside is that they aren't of practical use without using a `Selector`, which is *very* tricky API to use correctly.

Note that while it is possible to use non-blocking channels without a `Selector`, this means you have to busy-wait on the channel for the simplest reads and writes. It's not efficient.

The other issue is that only network channels and pipes support non-blocking mode.

### Asynchronous Channels

Asynchronous channels give us what we want: we don't need a `Selector` to use them, and our thread will never block when we use them.

However, it should be noted that asynchronous file I/O is not currently possible on the JVM. `AsynchronousFileChannel` is performing blocking I/O using a pool of blocked threads, which exactly what `flatMapBlocking` does, and shares the same drawbacks. It may be preferable to use a standard `FileChannel`, as you'll have more visibility and control over what's going on.

The asynchronous socket channels do *appear* to use non-blocking I/O, although they also have some form of internal thread pool as well. These should scale roughly as well as non-blocking channels. One downside is that there is no asynchronous datagram channel.
