---
id: essentials_resources
title:  "Resource Management"
---

NIO offers several objects, primarily channels, that consume resources (such as operating system file handles) that need to be released when no longer needed. If channels are not closed reliably, resource leaks can occur, causing a number of issues.

For this reason, ZIO-NIO provides such resources using the [ZIO `Scope` API][zio-scope]. For example, calling `FileChannel.open` will produce a value of `ZIO[Scope, IOException, FileChannel]`. The file will automatically be closed when the scope is closed. 

## Simple Usage

The most straight-forward way to use a scoped resource is with the `scoped` method:

```scala mdoc:silent
import zio._
import zio.nio.channels._
import zio.nio.file.Path
import java.io.IOException

def useChannel(f: FileChannel): ZIO[Any, IOException, Unit] = ???

val effect: ZIO[Any, IOException, Unit] = ZIO.scoped {
  FileChannel.open(Path("foo.txt"))
    .flatMap { fileChannel =>
      // fileChannel is only valid in the scope
      useChannel(fileChannel)
    }
}
```

In the above example, the `FileChannel` will be opened and then provided to the `useChannel` operation. The channel will always be closed when the scope is closed, regardless of whether the operation succeeds, fails, dies or is interrupted. As long as the channel is only used within the `Scope`, then we're guaranteed not to have leaks.

## Flexible Resource Scoping

Sometimes the resource lifecycle needs to extend beyond a lexical scope. An example of this is registering channels with a `Selector`. How can we do this using `Scope` while still avoiding the possibility of leaks?.

We can access the current scope using the `ZIO.scope` operator. The lifetime of other resources can be extended into this scope using the `Scope#extend` operator. When the scope is closed, all the other resources whose lifetimes have been extended into the scope will also be finalized.

```scala mdoc:silent
ZIO.scope.flatMap { scope =>

  val channel: IO[IOException, SocketChannel] = scope.extend(SocketChannel.open)

  // use channel, perhaps with a Selector
  channel.flatMap(_.flatMapNonBlocking(_.readChunk(10)))

}
// when the scope is closed all resources whose lifetimes have been extended into it will automatically be finalized.
```

You can continue to finalize the resource before the scope is closed, if you know it is no longer needed. This allows efficient use of the resource while still having the safety net of the scope to ensure the finalization happens even if there are failures, defects or interruptions.

The `zio.nio.channels.SelectorSpec` test demonstrates the use of scoping to ensure nothing leaks if an error occurs.

### Using `close` for Early Release

Every channel has a built-in early release in the form of the `close` method. Closing a channel more than once is a perfectly safe thing to do, so you can use `close` to release a channel's resources early. When the `Scope` of the channel later ends, `close` will be called again, but it will be a no-op.

[zio-scope]: https://zio.dev/docs/datatypes/datatypes_scope
