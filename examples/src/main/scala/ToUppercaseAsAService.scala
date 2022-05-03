package zio
package nio
package examples

import zio._
import zio.nio.channels.{BlockingNioOps, ServerSocketChannel, SocketChannel}
import zio.nio.charset.Charset
import zio.stream._

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

  private val upperCaseIfier: ZPipeline[Any, Nothing, Char, Char] =
    ZPipeline.identity[Char] >>> ZPipeline.map[Char, Char](_.toUpper)

  private def handleConnection(
    socket: SocketChannel
  )(implicit trace: Trace): ZIO[Any, IOException, Long] = {

    // this does the processing of the characters received over the channel via a pipeline
    // the stream of bytes from the channel is piped, then written back to the same channel's sink
    def transducer: ZPipeline[Any, IOException, Byte, Byte] =
      Charset.Standard.utf8.newDecoder.transducer() >>>
        upperCaseIfier >>>
        Charset.Standard.utf8.newEncoder.transducer()
    Console.printLine("Connection accepted") *>
      socket.flatMapBlocking { ops =>
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

  override def run: URIO[ZIOAppArgs, ExitCode] = {
    val portEff = ZIO.serviceWith[ZIOAppArgs](
      _.getArgs.toList.headOption
        .flatMap(s => catching(classOf[IllegalArgumentException]).opt(s.toInt))
        .getOrElse(7777)
    )

    portEff
      .flatMap(port =>
        ZIO.scoped {
          ServerSocketChannel.open.flatMapNioBlocking { (serverChannel, ops) =>
            InetSocketAddress.wildCard(port).flatMap { socketAddress =>
              serverChannel.bindTo(socketAddress) *>
                Console.printLine(s"Listening on $socketAddress") *>
                ops.acceptAndFork(handleConnection).forever
            }
          }
        }
      )
      .exitCode

  }

}
