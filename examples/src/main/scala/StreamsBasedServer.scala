package zio.nio.examples

import java.io.IOException

import zio._
import zio.duration._
import zio.nio.core.SocketAddress
import zio.nio.core.channels._
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
      .exitCode

  def server(port: Int): Managed[IOException, AsynchronousServerSocketChannel] =
    for {
      server        <- AsynchronousServerSocketChannel().toManagedNio
      socketAddress <- SocketAddress.inetSocketAddress(port).toManaged_
      _             <- server.bind(socketAddress).toManaged_
    } yield server

  def handleConnections[R <: console.Console](
    server: AsynchronousServerSocketChannel
  )(f: String => RIO[R, Unit]): ZStream[R, Throwable, Unit] =
    ZStream
      .repeatEffect(server.accept.toManagedNio.preallocate)
      .map(conn => ZStream.managed(conn.ensuring(console.putStrLn("Connection closed")).withEarlyRelease))
      .flatMapPar[R, Throwable, Unit](16) { connection =>
        connection
          .mapM {
            case (closeConn, channel) =>
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
