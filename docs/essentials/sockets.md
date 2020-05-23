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
import zio.nio._
import zio.nio.channels._
import zio.nio.core._
```

## Creating sockets

Creating server socket:

```scala mdoc:silent
val server = AsynchronousServerSocketChannel()
  .mapM { socket =>
    for {
      _ <- SocketAddress.inetSocketAddress("127.0.0.1", 1337) >>= socket.bind
      _ <- socket.accept.preallocate.flatMap(_.use(channel => doWork(channel).catchAll(ex => putStrLn(ex.getMessage))).fork).forever.fork
    } yield ()
  }.useForever

def doWork(channel: AsynchronousSocketChannel): ZIO[Console with Clock, Throwable, Unit] = {
  val process =
    for {
      chunk <- channel.read(3)
      str = chunk.toArray.map(_.toChar).mkString
      _ <- putStrLn(s"received: [$str] [${chunk.length}]")
    } yield ()

  process.whenM(channel.isOpen).forever
}
```

Creating client socket:

```scala mdoc:silent
val clientM: Managed[Exception, AsynchronousSocketChannel] = AsynchronousSocketChannel()
  .mapM { client =>
    for {
      host    <- InetAddress.localHost
      address <- SocketAddress.inetSocketAddress(host, 2552)
      _       <- client.connect(address)
    } yield client
  }
```

Reading and writing to socket:

```scala mdoc:silent
for {
  serverFiber <- server.fork
  clientFiber <- clientM.use(_.write(Chunk.fromArray(Array(1, 2, 3).map(_.toByte)))).fork
  _           <- clientFiber.join
  _           <- serverFiber.join
} yield ()
```
