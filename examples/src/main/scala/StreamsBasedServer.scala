import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.nio.core.SocketAddress
import zio.nio.channels._
import zio.stream._

object StreamsBasedServer extends App {

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    server(8080, 16).orDie
      .as(0)

  def server(port: Int, parallelism: Int): ZIO[ZEnv, Exception, Unit] =
    AsynchronousServerSocketChannel()
      .use(socket =>
        for {
          _ <- SocketAddress.inetSocketAddress("localhost", port) >>= socket.bind
          _ <- ZStream
                .repeatEffect(socket.accept.preallocate)
                .map(_.withEarlyRelease)
                .mapMPar(parallelism)(_.use((handleChannel _).tupled))
                .runDrain
        } yield ()
      )

  def handleChannel(
    closeConn: URIO[Any, Any],
    channel: AsynchronousSocketChannel
  ): ZIO[Clock with Console, Nothing, Unit] =
    for {
      _ <- console.putStrLn("Received connection")
      data <- ZStream
               .fromEffectOption(
                 channel.read(64).tap(_ => console.putStrLn("Read chunk")).orElse(ZIO.fail(None))
               )
               .take(4)
               .transduce(ZSink.utf8DecodeChunk)
               .run(Sink.foldLeft("")(_ + (_: String)))
      _ <- closeConn
      _ <- console.putStrLn(s"Read data: ${data.mkString}") *>
            clock.sleep(3.seconds) *>
            console.putStrLn("Done")
    } yield ()
}
