## Client + Server

```tut
object T {
  import scalaz.nio._
  import java.io.IOException
  import java.net.InetSocketAddress

  import scalaz.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
  import scalaz.zio.console._
  import scalaz.zio.{ App, IO }

  import scala.concurrent.duration._

  object ClientServer extends App {
    override def run(args: List[String]): IO[Nothing, ExitStatus] =
      myAppLogic
        .leftMap(new IOException(_))
        .attempt
        .map(_.fold(e => { e.printStackTrace(); 1 }, _ => 0))
        .map(ExitStatus.ExitNow(_))

    val address = new InetSocketAddress(1337)

    def myAppLogic: IO[Exception, Unit] =
      for {
        serverFiber <- server.fork
        clientFiber <- client.fork
        _ <- serverFiber.join
        _ <- clientFiber.join
      } yield ()

    def server: IO[Exception, Unit] = {
      def log(str: String): IO[IOException, Unit] = putStrLn("[Server] " + str)
      for {
        server <- AsynchronousServerSocketChannel()
        _      <- log(s"Listening on $address")
        _      <- server.bind(address)
        worker <- server.accept

        // TODO is this the right way of writing to the buffer?
        bufferDest <- Buffer.byte(8)
        n          <- worker.read(bufferDest)
        arr        <- bufferDest.array

        _ <- log(
              "Read: " + n.toString + " Bytes. Content: " + arr.toOption.get.mkString
            )
      } yield ()
    }

    def client: IO[Exception, Unit] = {
      def log(str: String): IO[IOException, Unit] = putStrLn("[Client] " + str)

      for {
        _      <- IO.sleep(1.second)
        client <- AsynchronousSocketChannel()
        _      <- client.connect(address)
        _      <- log("Connected.")

        // TODO is this the right way of reading from the buffer?
        bufferSrc <- Buffer.byte(8)
        arr       <- bufferSrc.array
        _         = arr.toOption.get.update(0, 1)

        _ <- log("Gonna write: " + arr.toOption.get.mkString)
        _ <- client.write(bufferSrc)
      } yield ()
    }

  }

}

```