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
val serverM = for {
  host <- InetAddress.localHost
  address <- SocketAddress.inetSocketAddress(host, 2552)
  server <- AsynchronousServerSocketChannel()
  _ <- server.bind(address)
  worker <- server.accept
} yield worker
```

Creating client socket:

```scala mdoc:silent
val clientM = for {
  host <- InetAddress.localHost
  address <- SocketAddress.inetSocketAddress(host, 2552)
  client <- AsynchronousSocketChannel()
  _ <- client.connect(address)
} yield client
```

Reading and writing to socket:

```scala mdoc:silent
for {
  server <- serverM
  client <- clientM
  _ <- client.write(Chunk.fromArray(Array(1,2,3).map(_.toByte)))
  chunk <- server.read(3)
} yield chunk
```
