# Addtions to ZIO-NIO

These are the changes and additions this branch has added to ZIO-NIO version **1.0.0-RC10** (the latest at this writing). The changes are:

* Only one module
* Blocking I/O operations can be interrupted
* Separate APIs for Blocking and Non-Blocking modes
* Asynchronous channel I/O operations can be interrupted
* Improvements to asynchronous channels API
* Streaming read and write APIs
* Better `WatchService` API including streaming
* Richer API for `InetSocketAddress`
* `Selector#select` can be interrupted

## Only One Module

The two modules — core and "high-level" — have been combined into a single module.

All resource acquisition is now done within `ZManaged`, with channel close being public to allow early release if desired. This removed the only significant difference between the core and high-level modules. A single module avoids duplication and makes the API simpler.

`ZManaged` supports resources being used beyond a single lexical scope, which is sometimes needed when using NIO. [The resource management docs](docs/essentials/resources.md) explain the details.

## Separate APIs for Non-Blocking and Interruptible Blocking

NIO has a number of methods that can be used in either blocking or non-blocking mode. A problem with this is they often have different behaviors in depending on the mode. For example, the `ServerSocketChannel#accept` method can return null, but only if the channel is non-blocking mode. It is desirable for the types to reflect this modal characteristic.

A larger issue is that of blocking channel operations, which are surprisingly tricky to make work well for an asynchronous effect system like ZIO. For blocking operations we want to:

* run the blocking operation on ZIO's `blocking` threadpool
* install an interrupt handler that closes the channel, as this is the only way to interrupt a blocking call on a NIO channel

Because RC10 ZIO-NIO does not currently setup interrupt handling, it isn't possible to interrupt fibers that are performing blocking I/O. This leads to problems such as programs not exiting when they receive a SIGTERM (control-C). 

Simply performing these two steps on every blocking read or write leads to poor performance, as both steps impose some overhead. The blocking pool overhead can be solved by running the entire NIO section of your program on the blocking pool (if you know you're using blocking operations). However, the interruption setup can't be done at such a high level, as it specific to each channel instance.

My solution to all the above factors is to offer bracketed access to custom blocking and non-blocking APIs. This allows a set of blocking operations to be performed safely while only paying the blocking setup cost once per bracket. This bracketed API is present on all channels that can operate in blocking mode.

For example, to perform a set of blocking operations on a channel:

```scala mdoc:silent
channel.useBlocking { ops =>
  ops.readChunk()
    .flatMap(bytes => console.putStrLn(s"Read ${bytes.length} bytes"))
  // more blocking operations using `ops`
}
```

`useBlocking` will put the channel into blocking mode, and perform the two setup steps described above: use the `blocking` pool and setup interruption handling. The `ops` argument provided to you offers all the blocking operations supported by the channel, including stream-based ones.

`useNonBlocking` will put the channel into non-blocking mode, and provide an operations argument with an API specific to non-blocking use.

## Asynchronous Channel Improvements

With current ZIO-NIO, fibers blocked waiting for a callback from an asynchronous channel cannot be interrupted. This leads to problems such as programs not exiting when they receive a SIGTERM (control-C). This branch fixes that.

Some NIO methods that were overlooked are also added.

## Streaming Read and Write

`ZStream`-based reading and `ZSink`-based writing are now built in for all channel types.

For asynchronous channels, the methods are built into channel:

```scala mdoc:silent
val readAllBytes: IO[IOException, Chunk[Byte]] =
  AsynchronousFileChannel.open(path).use { channel =>
    channel.stream(0).runCollect
  }
```

For blocking channels, the stream or sink should be used within `useBlocking`:

```scala mdoc:silent
val source: Stream[Nothing, Byte] = ???
val writeBytes: IO[IOException, Long] =
  FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { channel =>
    channel.useBlocking(ops => source.run(ops.sink()))
  }
```

While the stream and sink can be used with non-blocking channels, this probably isn't a good idea. Non-blocking channels will busy-wait on reads and writes until the channel is actually ready. This is worse than blocking the thread. Non-blocking channels really need to be used with a `Selector` to be useful.

## Streaming WatchService API

See [the example](examples/src/main/scala/StreamDirWatch.scala).
