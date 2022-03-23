package zio.nio.examples

import zio.nio.InetSocketAddress
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.stream._
import zio.{Clock, Console, ExitCode, RIO, Scope, URIO, ZIO, ZIOAppDefault, ZTraceElement, durationInt}

object StreamsBasedServer extends ZIOAppDefault {

  def run: URIO[Console with Clock with Console, ExitCode] =
    ZStream
      .scoped(server(8080))
      .flatMap(handleConnections(_) { chunk =>
        Console.printLine(s"Read data: ${chunk.mkString}") *>
          Clock.sleep(2.seconds) *>
          Console.printLine("Done").ignore
      })
      .runDrain
      .orDie
      .exitCode

  def server(port: Int)(implicit trace: ZTraceElement): ZIO[Scope, Exception, AsynchronousServerSocketChannel] =
    for {
      server        <- AsynchronousServerSocketChannel.open
      socketAddress <- InetSocketAddress.wildCard(port)
      _             <- server.bindTo(socketAddress)
    } yield server

  def handleConnections[R <: Console](
    server: AsynchronousServerSocketChannel
  )(f: String => RIO[R, Unit])(implicit trace: ZTraceElement): ZStream[R, Throwable, Unit] =
    ZStream
      .repeatZIO(server.accept.preallocate)
      .map(conn => ZStream.scoped(conn.ensuring(Console.printLine("Connection closed").ignore).withEarlyRelease))
      .flatMapPar[R, Throwable, Unit](16) { connection =>
        connection.mapZIO { case (closeConn, channel) =>
          for {
            _ <- Console.printLine("Received connection")
            data <- ZStream
                      .fromZIOOption(
                        channel.readChunk(64).tap(_ => Console.printLine("Read chunk")).orElse(ZIO.fail(None))
                      )
                      .flattenChunks
                      .take(4)
                      .via(ZPipeline.utf8Decode)
                      .run(Sink.foldLeft("")(_ + (_: String)))
            _ <- closeConn
            _ <- f(data)
          } yield ()
        }
      }
}
