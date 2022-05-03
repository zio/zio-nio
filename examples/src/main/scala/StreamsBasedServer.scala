package zio.nio.examples

import zio.nio.InetSocketAddress
import zio.nio.channels.AsynchronousServerSocketChannel
import zio.stream._
import zio.{Clock, Console, ExitCode, RIO, Scope, Trace, UIO, ZIO, ZIOAppDefault, durationInt}

object StreamsBasedServer extends ZIOAppDefault {

  def run: UIO[ExitCode] =
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

  def server(port: Int)(implicit trace: Trace): ZIO[Scope, Exception, AsynchronousServerSocketChannel] =
    for {
      server        <- AsynchronousServerSocketChannel.open
      socketAddress <- InetSocketAddress.wildCard(port)
      _             <- server.bindTo(socketAddress)
    } yield server

  def handleConnections[R](
    server: AsynchronousServerSocketChannel
  )(f: String => RIO[R, Unit])(implicit trace: Trace): ZStream[R, Throwable, Unit] =
    ZStream
      .scoped(server.accept)
      .forever
      .mapZIOPar[R, Throwable, Unit](16) { channel =>
        for {
          _ <- Console.printLine("Received connection")
          data <- ZStream
                    .fromZIOOption(
                      channel.readChunk(64).tap(_ => Console.printLine("Read chunk")).orElse(ZIO.fail(None))
                    )
                    .flattenChunks
                    .take(4)
                    .via(ZPipeline.utf8Decode)
                    .run(ZSink.foldLeft("")(_ + (_: String)))
          _ <- channel.close
          _ <- Console.printLine("Connection closed")
          _ <- f(data)
        } yield ()
      }
}
