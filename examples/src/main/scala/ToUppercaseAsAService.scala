package zio
package nio
package examples

import zio.nio.channels.{ManagedBlockingNioOps, ServerSocketChannel, SocketChannel}
import zio.nio.charset.Charset
import zio.stream.{ZPipeline, ZStream}

import java.io.IOException
import scala.util.control.Exception._

/**
 * `toUpperCase` as a service.
 *
 * Using ZIO-NIO and ZIO streams to build a very silly TCP service. Listens on port 7777 by default.
 *
 * Send it UTF-8 text and it will send back the uppercase version. Amazing!
 */
object ToUppercaseAsAService extends ZIOAppDefault {

  private val upperCaseIfier = new ZPipeline[Any, Nothing, Char, Char] {
    override def apply[Env1 <: Any, Err1 >: Nothing](stream: ZStream[Env1, Err1, Char])(implicit
      trace: ZTraceElement
    ): ZStream[Env1, Err1, Char] = stream.map(_.toUpper)
  }

  private def handleConnection(
    socket: SocketChannel
  )(implicit trace: ZTraceElement): ZIO[Any with Console with Clock, IOException, Long] = {

    // this does the processing of the characters received over the channel via a pipeline
    // the stream of bytes from the channel is piped, then written back to the same channel's sink
    def transducer: ZPipeline[Any, IOException, Byte, Byte] =
      Charset.Standard.utf8.newDecoder.transducer() >>>
        upperCaseIfier >>>
        Charset.Standard.utf8.newEncoder.transducer()
    Console.printLine("Connection accepted") *>
      socket.useBlocking { ops =>
        ops
          .stream()
          .via(transducer)
          .run(ops.sink())
          .tapBoth(
            e => Console.printLine(s"Connection error: $e"),
            i => Console.printLine(s"Connection ended, wrote $i bytes")
          )
      }
  }

  override def run: URIO[zio.ZEnv with ZIOAppArgs, ExitCode] = {
    val portEff = ZIO.serviceWith[ZIOAppArgs](
      _.getArgs.toList.headOption
        .flatMap(s => catching(classOf[IllegalArgumentException]).opt(s.toInt))
        .getOrElse(7777)
    )

    portEff
      .flatMap(port =>
        ServerSocketChannel.open.useNioBlocking { (serverChannel, ops) =>
          InetSocketAddress.wildCard(port).flatMap { socketAddress =>
            serverChannel.bindTo(socketAddress) *>
              Console.printLine(s"Listening on $socketAddress") *>
              ops.acceptAndFork(handleConnection).forever
          }
        }
      )
      .exitCode

  }

}
