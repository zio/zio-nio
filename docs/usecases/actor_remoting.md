---
id: usecases_actor_remoting
title:  "Actor Remoting"
---

Imagine we would like to use `ZIO-actors` just like `akka-actor` with remote functionality.
For now `ZIO-actors` does not provide actor remoting, but no worries - we're going to make it from scratch with `ZIO-NIO`!

## Recalling Akka Remote

In `akka-remote` we were providing additional `application.conf` for our project to indicate which `host` and `port`
an `ActorSystem` has to use. Additionally to acquire stub for some remote actor we were obligated to use `actorSelection` selection
method to receive `ActorRef` e.g.:

```scala
val remoteActor = context.actorSelection("akka.tcp://ServerApp@127.0.0.1:2552/user/ServerActor")
```

## Design outline

For our remote functionality we need an `ActorSystem` that opens the server socket for incoming messages at given host and port.
It also must be able to spawn new actors and register them in internal map with given name. Here for simplicity our actor's
structure will be flat so without any hierarchy.
And last it must have stub creator for remote actors for some host, port and name.

Now let's dive into the code, with explanation.
The complete script can be found [HERE](https://gist.github.com/mtsokol/ca7a94833323df84242c87ad2d5ce443) and at the page bottom. 

## application.conf

Same as we would've been doing it in Akka, we provide proper configuration in `application.conf` file:

```
zio.actor.remote {
  hostname = "127.0.0.1"
  port = 2552
}
```

## ActorSystem

First all required imports:

```scala mdoc:silent
import com.typesafe.config.ConfigFactory
import zio._
import zio.actors.Actor.Stateful
import zio.actors.{Actor, Supervisor}
import zio.nio._
import zio.nio.channels._
```

So now let's look at `ActorSystem` constructor's parameters:

```scala mdoc:silent
case class ActorSystem[F[+_]](configPathPrefix: Option[String] = None,
                             actorMap: Map[String, Actor[Exception, F]] = Map.empty,
                             private[ActorSystem] val socket: Option[AsynchronousSocketChannel] = None)
                            (implicit decoder: Array[Byte] => (String, F[_])) {
  //all the code...                          
}
```

That might look overwhelming but it's only what was discussed - an optional `configPathPrefix` in case our configuration in 
`application.conf` is somewhat nested, `actorMap` for keeping all registered actors in our `ActorSystem`, 
`socket` that handles all incoming messages from remote actors to the local ones, and implicit `decoder` to deserialize received
messages into `(name, message)` tuple.

Enough about parameters, let's see the internals. First we need to get mentioned configuration so nothing fancy:

```scala
private def getParams: UIO[(String, Int)] = {
    val config = ConfigFactory.load()
    val prefix = configPathPrefix.map(_ + ".").getOrElse("")
    val hostname = config.getString(prefix + "zio.actor.remote.hostname")
    val port = config.getInt("zio.actor.remote.port")
    IO.succeedLazy((hostname, port))
  }
```

Now we finally use `ZIO-NIO` sockets. Here we're opening a socket for receiving incoming messages from other 
remote `ActorSystems`s:

```scala
private def startSystemServer(hostname: String, port: Int): IO[Exception, ActorSystem[F]] = for {
    host <- InetAddress.byName(hostname)
    address <- SocketAddress.inetSocketAddress(host, port)
    server <- AsynchronousServerSocketChannel()
    _ <- server.bind(address)
    worker <- server.accept
  } yield copy[F](socket = Some(worker))
```

Next is `read` method that constantly tries to read from that server socket. When it receives whole message it decodes 
it with provided implicit `decoder` and transmit it to proper registered local actor:

```scala
def read(): IO[Exception, Unit] = for {
    socket <- socket match {
      case Some(value) => IO.succeedLazy(value)
      case None => IO.fail(new Exception("Actor System server not yet started. First use `startSystemServer`"))
    }
    raw <- socket.read(capacity = 200)
    (name: String, msg: F[_]) = decoder(raw.toArray)
    actor = actorMap.get(name)

    send: IO[Exception, Any] = actor.map(_.!(msg)) match {
      case Some(value) => value
      case None => IO.fail(new Exception("Actor not found in this system."))
    }
    _ <- send
  } yield ()
```

Ok, the hard part is behind us. Now we have to provide methods for instantiating new actors and registering them in system's map:

```scala
def actorOf[S](name: String, initial: S, stateful: Stateful[S, Exception, F]): scalaz.zio.ZIO[Any, Nothing, (Actor[Exception, F], ActorSystem[F])] = for {
    actor <- Actor.stateful(Supervisor.none)(initial)(stateful)
  } yield (actor, copy[F](actorMap = actorMap + (name -> actor)))
```

Now let's focus on remote actor stub API. First we need `remoteHandler` that basically hides from us the fact that actor is not here!
It handles incoming messages by encoding and writing them to the socket:

```scala
type Socket = AsynchronousSocketChannel
  type Name = String
  type State[U[_], A] = ((Socket, Name), (U[A], Name) => (Array[Byte], A))

  private def remoteHandler[U[_]]: Stateful[((Socket, Name), (U[_], Name) => (Array[Byte], _)), Exception, U] = new Stateful[Socket, Exception, U] {
    override def receive[A](state: State[U, A], msg: U[A]): IO[Exception, (State[U, A], A)] = {
      val ((socket, name), encoder) = state
      val (encoded, remainder) = encoder(msg, name)
      for {
        encodedMsg <- IO.succeedLazy(Chunk.fromArray(encoded))
        _ <- socket.write(encodedMsg)
      } yield (state, remainder)
    }
  }
```

Implemented handler will be used in `acquireRemoteActor` that instantiates our stub. Such transparency is
handy because we don't care where the actor is. So here we create client socket, connect to it, and 
instantiate new actor that will perform remote communication:

```scala
def acquireRemoteActor[U[_]](address: String, port: Int, name: String)(implicit encoder: (U[_], String) => (Array[Byte], _)): Task[Actor[Exception, U]] = for {
    host <- InetAddress.byName(address)
    address <- SocketAddress.inetSocketAddress(host, port)
    client <- AsynchronousSocketChannel()
    _ <- client.connect(address)
    actor <- Actor.stateful(Supervisor.none)(((client, name), encoder))(remoteHandler[U])
  } yield actor
```

The last but not least. We supply `ActorSystem` object with factory method that utilizes described internals:

```scala
object ActorSystem {
  type Name = String

  def create[F[_]](implicit decoder: Array[Byte] => (Name, F[_])): ZIO[Any, Exception, ActorSystem[F]] = for {
    actorSystem <- IO.succeedLazy(ActorSystem[F]())
    params <- actorSystem.getParams
    (host, port) = params
    actorSystemStarted <- actorSystem.startSystemServer(host, port)
  } yield actorSystemStarted
}
```

## The Program

Finally in our end-user-like module we use all described components. First we provide the simplest handler for messages:

```scala
def handler[Option[_]]: Stateful[Int, Exception, Option] = new Stateful[Int, Exception, Option] {
  override def receive[A](state: Int, msg: Option): IO[Exception, (Int, A)] =
    msg match {
      case Some(x) => IO.succeedLazy((state + 1, x))
      case None => IO.succeedLazy((0, "reset"))
    }
}
```

And the main program:

```scala
val program = for {
    system <- ActorSystem.create[Option]
   
    actor <- system.actorOf[Int]("local-actor", 0, handler)

    fiber <- actor.read().forever.fork
    _ <- fiber.interrupt

    remoteActor <- system.acquireRemoteActor[Option]("localhost", 2552, "remote-actor")
    _ <- remoteActor.!(Some(12))
} yield ()
```

## References

The complete script is [HERE](https://gist.github.com/mtsokol/ca7a94833323df84242c87ad2d5ce443)
