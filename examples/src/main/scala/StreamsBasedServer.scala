package zio.nio.examples

import zio._
import zio.clock.Clock
import zio.duration._
import zio.nio.InetSocketAddress
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.stream._

object StreamsBasedServer extends App {

  def run(args: List[String]): URIO[zio.console.Console with Clock with zio.console.Console, ExitCode] =
    ZStream
      .managed(server(8080))
      .flatMap(handleConnections(_) { chunk =>
        console.putStrLn(s"Read data: ${chunk.mkString}") *>
          clock.sleep(2.seconds) *>
          console.putStrLn("Done").ignore
      })
      .runDrain
      .orDie
      .exitCode

  def server(port: Int): Managed[Exception, AsynchronousServerSocketChannel] =
    for {
      server        <- AsynchronousServerSocketChannel.open
      socketAddress <- InetSocketAddress.wildCard(port).toManaged_
      _             <- server.bindTo(socketAddress).toManaged_
    } yield server

  def handleConnections[R <: console.Console](
    server: AsynchronousServerSocketChannel
  )(f: String => RIO[R, Unit]): ZStream[R, Throwable, Unit] =
    ZStream
      .repeatEffect(server.accept.preallocate)
      .map(conn => ZStream.managed(conn.ensuring(console.putStrLn("Connection closed").ignore).withEarlyRelease))
      .flatMapPar[R, Throwable, Unit](16) { connection =>
        connection
          .mapM { case (closeConn, channel) =>
            for {
              _    <- console.putStrLn("Received connection")
              data <- ZStream
                        .fromEffectOption(
                          channel.readChunk(64).tap(_ => console.putStrLn("Read chunk")).orElse(ZIO.fail(None))
                        )
                        .flattenChunks
                        .take(4)
                        .transduce(ZTransducer.utf8Decode)
                        .run(Sink.foldLeft("")(_ + (_: String)))
              _    <- closeConn
              _    <- f(data)
            } yield ()
          }
      }
}
