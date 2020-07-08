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
import zio.nio.core.channels._
import zio.nio.core._
import zio.nio.core.charset._
import java.io.IOException
```

## Creating sockets

Creating server socket:

```scala mdoc:silent
val server = AsynchronousServerSocketChannel().toManagedNio
  .mapM { socket =>
    for {
      address <- SocketAddress.inetSocketAddress("127.0.0.1", 1337)
      _ <- socket.bind(address)
      _ <- socket.accept.toManagedNio.preallocate.flatMap(_.use(channel => doWork(channel).catchAll(ex => putStrLn(ex.getMessage))).fork).forever.fork
    } yield ()
  }.useForever

def doWork(channel: AsynchronousSocketChannel): ZIO[Console, Exception, Unit] = {
  val process =
    for {
      byteChunk <- channel.readChunk(3)
      charChunk <- Charset.Standard.utf8.decodeChunk(byteChunk)
      str = charChunk.mkString
      _ <- putStrLn(s"received: [$str] [${str.length}]")
    } yield ()

  process.whenM(channel.isOpen).forever
}
```

Creating client socket:

```scala mdoc:silent
val clientM: Managed[Exception, AsynchronousSocketChannel] = AsynchronousSocketChannel().toManagedNio
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
  clientFiber <- clientM.use(_.writeChunk(Chunk(1, 2, 3).map(_.toByte))).fork
  _           <- clientFiber.join
  _           <- serverFiber.join
} yield ()
```
