---
id: essentials_sockets
title:  "Socket Channel"
---

`AsynchronousSocketChannel` and `AsynchronousServerSocketChannel` provide methods for communicating with remote clients.

Required imports for snippets:

```scala mdoc:silent
import zio._
import zio.Console._
import zio.nio.channels._
import zio.nio._
```

## Creating sockets

Creating a server socket:

```scala mdoc:silent
val server = ZIO.scoped {
  AsynchronousServerSocketChannel.open
    .flatMap { socket =>
      for {
        address <- InetSocketAddress.hostName("127.0.0.1", 1337)
        _ <- socket.bindTo(address)
        _ <- socket.accept.flatMap(channel => doWork(channel).catchAll(ex => printLine(ex.getMessage)).fork).forever.fork
      } yield ()
    } *> ZIO.never
}

def doWork(channel: AsynchronousSocketChannel): ZIO[Any, Throwable, Unit] = {
  val process =
    for {
      chunk <- channel.readChunk(3)
      str = chunk.toArray.map(_.toChar).mkString
      _ <- printLine(s"received: [$str] [${chunk.length}]")
    } yield ()

  process.whenZIO(channel.isOpen).forever
}
```

Creating a client socket:

```scala mdoc:silent
val clientM: ZIO[Scope, Exception, AsynchronousSocketChannel] = AsynchronousSocketChannel.open
  .flatMap { client =>
    for {
      host    <- InetAddress.localHost
      address <- InetSocketAddress.inetAddress(host, 2552)
      _       <- client.connect(address)
    } yield client
  }
```

Reading and writing to a socket:

```scala mdoc:silent
for {
  serverFiber <- server.fork
  _ <- ZIO.scoped(clientM.flatMap(_.writeChunk(Chunk.fromArray(Array(1, 2, 3).map(_.toByte)))))
  _           <- serverFiber.join
} yield ()
```
