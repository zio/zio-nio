package zio
package nio
package examples


import zio.Clock
import zio.nio.channels.{ManagedBlockingNioOps, ServerSocketChannel, SocketChannel}
import zio.nio.charset.Charset
import zio.stream.ZTransducer

import java.io.IOException
import scala.util.control.Exception._
import zio.{ Console, Console, ZIOAppDefault }

/**
 * `toUpperCase` as a service.
 *
 * Using ZIO-NIO and ZIO streams to build a very silly TCP service. Listens on port 7777 by default.
 *
 * Send it UTF-8 text and it will send back the uppercase version. Amazing!
 */
object ToUppercaseAsAService extends ZIOAppDefault {

  private val upperCaseIfier = ZTransducer.identity[Char].map(_.toUpper)

  private def handleConnection(socket: SocketChannel): ZIO[Any with Console with Clock, IOException, Long] = {

    // this does the processing of the characters received over the channel via a transducer
    // the stream of bytes from the channel is transduced, then written back to the same channel's sink
    def transducer =
      Charset.Standard.utf8.newDecoder.transducer() >>>
        upperCaseIfier >>>
        Charset.Standard.utf8.newEncoder.transducer()
    Console.printLine("Connection accepted") *>
      socket.useBlocking { ops =>
        ops
          .stream()
          .transduce(transducer)
          .run(ops.sink())
          .tapBoth(
            e => Console.printLine(s"Connection error: $e"),
            i => Console.printLine(s"Connection ended, wrote $i bytes")
          )
      }
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val port = args.headOption
      .flatMap(s => catching(classOf[IllegalArgumentException]).opt(s.toInt))
      .getOrElse(7777)

    ServerSocketChannel.open.useNioBlocking { (serverChannel, ops) =>
      InetSocketAddress.wildCard(port).flatMap { socketAddress =>
        serverChannel.bindTo(socketAddress) *>
          Console.printLine(s"Listening on $socketAddress") *>
          ops.acceptAndFork(handleConnection).forever
      }
    }.exitCode

  }

}
