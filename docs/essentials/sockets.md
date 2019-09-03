---
id: essentials_sockets
title:  "Socket Channel"
---

`AsynchronousSocketChannel` and `AsynchronousServerSocketChannel` provide methods for communicating with remote clients.

Required imports for snippets:

```scala mdoc:silent
import zio._
import zio.nio._
import zio.nio.channels._
```

## Creating sockets

Creating server socket:

```scala mdoc:silent
val serverM: Managed[Exception, AsynchronousSocketChannel] =
  AsynchronousServerSocketChannel()
    .mapM { server =>
      for {
        host    <- InetAddress.localHost
        address <- SocketAddress.inetSocketAddress(host, 2552)
        _       <- server.bind(address)
      } yield server
    }
    .flatMap(_.accept)
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
  serverFiber <- serverM.use { server =>
                  server.read(3)
                }.fork
  clientFiber <- clientM.use { client =>
                  client.write(Chunk.fromArray(Array(1, 2, 3).map(_.toByte)))
                }.fork
  chunk <- serverFiber.join
  _     <- clientFiber.join
} yield chunk
```
