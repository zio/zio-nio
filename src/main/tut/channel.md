## Client + Server

```tut
object T {
  import scalaz.nio._
  import java.io.IOException
  import scalaz.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
  import scalaz.zio.console._
  import scalaz.zio.duration._
  import scalaz.zio.{ App, Chunk, IO }

  object ClientServer extends App {
    override def run(args: List[String]): IO[Nothing, ExitStatus] =
      myAppLogic
        .attempt
        .map(_.fold(e => { e.printStackTrace(); 1 }, _ => 0))
        .map(ExitStatus.ExitNow(_))

    def myAppLogic: IO[Exception, Unit] =
      for {
        localhost <- InetAddress.localHost
        address <- SocketAddress.inetSocketAddress(localhost, 1337)
        serverFiber <- server(address).fork
        clientFiber <- client(address).fork
        _ <- serverFiber.join
        _ <- clientFiber.join
      } yield ()

    def server(address: SocketAddress): IO[Exception, Unit] = {
      def log(str: String): IO[IOException, Unit] = putStrLn("[Server] " + str)
      for {
        server <- AsynchronousServerSocketChannel()
        _      <- log(s"Listening on $address")
        _      <- server.bind(address)
        worker <- server.accept

        // TODO is this the right way of writing to the buffer?
        chunkDest <- worker.read(8)
        arr        = chunkDest.toArray

        _ <- log(
              "Content: " + arr.mkString
            )
        _ <- server.close
      } yield ()
    }

    def client(address: SocketAddress): IO[Exception, Unit] = {
      def log(str: String): IO[IOException, Unit] = putStrLn("[Client] " + str)

      for {
        _      <- IO.sleep(1.second)
        client <- AsynchronousSocketChannel()
        _      <- client.connect(address)
        _      <- log("Connected.")

        // TODO is this the right way of reading from the buffer?
        chunkSrc  <- IO.succeed(Chunk.fromArray(Array[Byte](1)))

        _ <- log("Gonna write: " + chunkSrc.mkString)
        _ <- client.write(chunkSrc)
        _ <- client.close
      } yield ()
    }

  }

  ClientServer.run(List())
}


```