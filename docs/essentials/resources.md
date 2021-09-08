---
id: essentials_resources
title:  "Resource Management"
---

NIO offers several objects, primarily channels, that consume resources (such as operating system file handles) that need to be released when no longer needed. If channels are not closed reliably, resource leaks can occur, causing a number of issues.

For this reason, ZIO-NIO provides such resources using the [ZIO `ZManaged` API][zio-managed]. For example, calling `FileChannel.open` will produce a value of `ZManaged[Blocking, IOException, FileChannel]`. The file will not actually be opened until the managed value is *used*. 

## Simple Usage

The most straight-forward way to use a managed resource is with the `use` method:

```scala mdoc:silent
import zio._
import zio.blocking.Blocking
import zio.nio.channels._
import zio.nio.file.Path
import java.io.IOException

def useChannel(f: FileChannel): ZIO[Blocking, IOException, Unit] = ???

val effect: ZIO[Blocking, IOException, Unit] = FileChannel.open(Path("foo.txt"))
  .use { fileChannel =>
    // fileChannel is only valid in this lexical scope
    useChannel(fileChannel)
  }
```

In the above example, the `FileChannel` will be opened and then provided to the function passed to `use`. The channel will always be closed when the `use` function completes, regardless of whether the operation succeeds, fails, dies or is interrupted. As long as the channel is only used within the function passed to `use`, then we're guaranteed not to have leaks.

## Flexible Resource Scoping

Sometimes there are situations where `ZManaged#use` is too limiting, because the resource lifecycle needs to extend beyond a lexical scope. An example of this is registering channels with a `Selector`. How can we do this using `ZManaged` while still avoiding the possibility of leaks? One way is to use [the "scope" feature of `ZManaged`][zio-scope].

A scope is itself a managed resource. Other managed resources can be attached to a scope, which gives them the same lifecycle as the scope. When the scope is released, all the other resources that have been attached to it will also be released.

```scala mdoc:silent
ZManaged.scope.use { scope =>

  val channel: IO[IOException, SocketChannel] = scope(SocketChannel.open).map {
    case (earlyRelease @ _, channel) => channel
  }

  // use channel, perhaps with a Selector
  channel.flatMap(_.useNonBlocking(_.readChunk(10)))

}
// the scope has now been released, as have all the resources attached to it
```

Note that `scope` returns both the resource and an "early release" effect. This allows you to release the resource before the scope exits, if you know it is no longer needed. This allows efficient use of the resource while still having the safety net of the scope to ensure the release happens even if there are failures, defects or interruptions.

The `zio.nio.channels.SelectorSpec` test demonstrates the use of scoping to ensure nothing leaks if an error occurs.

### Using `close` for Early Release

In the case of channels, we don't actually need the early release features that `ZManaged` provides, as every channel has a built-in early release in the form of the `close` method. Closing a channel more than once is a perfectly safe thing to do, so you can use `close` to release a channel's resources early. When the `ZManaged` scope of the channel later ends, `close` will be called again, but it will be a no-op.

## Manual Resource Management

It is also possible to switch to completely manual resource management. [The `reserve` method][zio-reserve] can be called on any `ZManaged` value, which gives you the acquisition and release of the resource as two separate effect values that you can use as you like. If you use these reservation effects directly, it is entirely up to you to avoid leaking resources. This requires code to be written very carefully, and an understanding the finer details of how failures, defects and interruption work in ZIO.

[zio-managed]: https://zio.dev/docs/datatypes/datatypes_managed
[zio-scope]: https://javadoc.io/doc/dev.zio/zio_2.13/latest/zio/ZManaged$.html#scope:zio.Managed[Nothing,zio.ZManaged.Scope]
[zio-reserve]: https://javadoc.io/doc/dev.zio/zio_2.13/latest/zio/ZManaged.html#reserve:zio.UIO[zio.Reservation[R,E,A]]
