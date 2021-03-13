---
id: essentials_sockets
title:  "Socket Channel"
---

`AsynchronousSocketChannel` and `AsynchronousServerSocketChannel` provide methods for communicating with remote clients.

Required imports for snippets:

```scala mdoc:silent
import zio._
import zio.clock._
import zio.console._
import zio.nio.channels._
import zio.nio.core._
```

## Creating sockets

Creating a server socket:

```scala mdoc:silent
val server = AsynchronousServerSocketChannel()
  .mapM { socket =>
    for {
      sockAddr <- InetSocketAddress.hostNameResolved("127.0.0.1", 1337) 
      _ <- socket.bindTo(sockAddr)
      _ <- socket.accept.preallocate.flatMap(_.use(channel => doWork(channel).catchAll(ex => putStrLn(ex.getMessage))).fork).forever.fork
    } yield ()
  }.useForever

def doWork(channel: AsynchronousSocketChannel): ZIO[Console with Clock, Throwable, Unit] = {
  val process =
    for {
      chunk <- channel.readChunk(3)
      str = chunk.toArray.map(_.toChar).mkString
      _ <- putStrLn(s"received: [$str] [${chunk.length}]")
    } yield ()

  process.whenM(channel.isOpen).forever
}
```

Creating a client socket:

```scala mdoc:silent
val clientM: Managed[Exception, AsynchronousSocketChannel] = AsynchronousSocketChannel()
  .mapM { client =>
    for {
      address <- InetSocketAddress.localHost(2552)
      _       <- client.connect(address)
    } yield client
  }
```

Reading and writing to a socket:

```scala mdoc:silent
for {
  serverFiber <- server.fork
  clientFiber <- clientM.use(_.writeChunk(Chunk.fromArray(Array(1, 2, 3).map(_.toByte)))).fork
  _           <- clientFiber.join
  _           <- serverFiber.join
} yield ()
```
