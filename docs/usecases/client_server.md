---
id: usecases_client_server
title:  "Client Server"
---

Let's implement simple client-server example and send some `Chunk` across.

## Imports

```scala mdoc:silent
import zio.nio._
import java.io.IOException
import zio.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import zio.clock.Clock
import zio.console._
import zio.duration._
import zio.{ App, Chunk, IO, ZIO }
```

## Client & Server

First we need a server for opening `ServerSocketChannel` for some given address. Then we try to read 
given amount of bytes as a chunk and print them: 

```scala mdoc:silent
def server(address: SocketAddress): ZIO[Console, Exception, Unit] = {
  def log(str: String): ZIO[Console, IOException, Unit] = putStrLn("[Server] " + str)
  for {
    _ <- AsynchronousServerSocketChannel().use { server =>
          for {
            _ <- log(s"Listening on $address")
            _ <- server.bind(address)
            chunkDest <- server.accept.use { worker =>
                          worker.read(8)
                        }
            arr = chunkDest.toArray
            _   <- log("Content: " + arr.mkString)
          } yield ()
        }
  } yield ()
}
```

On the other side there's a client that opens it's `SocketChannel` at given address and writes some content to it:

```scala mdoc:silent
def client(address: SocketAddress): ZIO[Clock with Console, Exception, Unit] = {
  def log(str: String): ZIO[Console, IOException, Unit] = putStrLn("[Client] " + str)

  for {
    _ <- ZIO.sleep(1.second)
    _ <- AsynchronousSocketChannel().use { client =>
          for {
            _ <- client.connect(address)
            _ <- log("Connected.")

            chunkSrc <- IO.succeed(Chunk.fromArray(Array[Byte](1)))

            _ <- log("Gonna write: " + chunkSrc.mkString)
            _ <- client.write(chunkSrc)
          } yield ()
        }
  } yield ()
}
```

Try to run the program with your own custom input array. What does happen when you pass more than 8 elements in array?
Also try running without any `write`.

## Program

Both implemented sites need to be bound with common `InetAddress` and forked:

```scala mdoc:silent
val myAppLogic: ZIO[Clock with Console, Exception, Unit] =
  for {
    localhost <- InetAddress.localHost
    address <- SocketAddress.inetSocketAddress(localhost, 1337)
    serverFiber <- server(address).fork
    clientFiber <- client(address).fork
    _ <- serverFiber.join
    _ <- clientFiber.join
  } yield ()
```

Let's run it as `ZIO` App:

```scala mdoc:silent
object ClientServer extends App {
  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    myAppLogic
      .orDie
      .fold(_ => 1, _ => 0)
}
```

## Output

All foregoing snippets result in output:

```
[Server] Listening on zio.nio.InetSocketAddress@276fd452
[Client] Connected.
[Client] Gonna write: 1
[Server] Content: 1
```

For some other array larger than 8 elements:

```
[Server] Listening on zio.nio.InetSocketAddress@18c5321e
[Client] Connected.
[Client] Gonna write: 12345678910
[Server] Content: 12345678
```
