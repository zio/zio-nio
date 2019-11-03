package zio.nio.examples

import zio._
import zio.duration._
import zio.nio._
import zio.nio.channels._
import zio.stream._

object StreamsBasedServer extends App {

  def run(args: List[String]) =
    ZStream
      .managed(server(8080))
      .flatMap(handleConnections(_) { chunk =>
        console.putStrLn(s"Read data: ${chunk.mkString}") *>
          clock.sleep(2.seconds) *>
          console.putStrLn("Done")
      })
      .runDrain
      .orDie
      .as(0)

  def server(port: Int): Managed[Exception, AsynchronousServerSocketChannel] =
    for {
      server        <- AsynchronousServerSocketChannel()
      socketAddress <- SocketAddress.inetSocketAddress(port).toManaged_
      _             <- server.bind(socketAddress).toManaged_
    } yield server

  def handleConnections[R <: console.Console](
    server: AsynchronousServerSocketChannel
  )(f: String => RIO[R, Unit]): ZStream[R, Throwable, Unit] =
    ZStream
      .repeatEffect(server.accept)
      .map { conn =>
        ZStream.managed(conn.ensuring(console.putStrLn("Connection closed")).withEarlyRelease)
      }
      .flatMapPar[R, Throwable, Unit](16) { connection =>
        connection
          .mapM {
            case (closeConn, channel) =>
              for {
                _ <- console.putStrLn("Received connection")
                data <- ZStream
                         .fromPull(channel.read(64).tap(_ => console.putStrLn("Read chunk")).orElse(ZIO.fail(None)))
                         .take(4)
                         .transduce(ZSink.utf8DecodeChunk)
                         .run(Sink.foldLeft("")(_ + (_: String)))
                _ <- closeConn
                _ <- f(data)
              } yield ()
          }
      }
}
