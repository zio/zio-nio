package zio.nio.channels

import java.nio.channels.CancelledKeyException

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.core.{ Buffer, ByteBuffer, SocketAddress }
import zio.nio.core.channels.SelectionKey.Operation
import zio.nio.BaseSpec
import zio.test._
import zio.test.Assertion._

object SelectorSpec extends BaseSpec {

  override def spec =
    suite("SelectorSpec")(
      testM("read/write") {
        for {
          started     <- Promise.make[Nothing, SocketAddress]
          serverFiber <- server(started).fork
          addr        <- started.await
          clientFiber <- client(addr).fork
          _           <- serverFiber.join
          message     <- clientFiber.join
        } yield assert(message)(equalTo("Hello world"))
      }
    )

  def byteArrayToString(array: Array[Byte]): String =
    array.takeWhile(_ != 10).map(_.toChar).mkString.trim

  def safeStatusCheck(statusCheck: IO[CancelledKeyException, Boolean]): IO[Nothing, Boolean] =
    statusCheck.fold(_ => false, identity)

  def server(started: Promise[Nothing, SocketAddress]): ZIO[Clock with Blocking, Exception, Unit] = {
    def serverLoop(
      selector: Selector,
      channel: ServerSocketChannel,
      buffer: ByteBuffer
    ): ZIO[Blocking, Exception, Unit] =
      for {
        _            <- selector.select
        selectedKeys <- selector.selectedKeys
        _            <- IO.foreach(selectedKeys) { key =>
                          IO.whenM(safeStatusCheck(key.isAcceptable)) {
                            for {
                              clientOpt <- channel.accept
                              client     = clientOpt.get
                              _         <- client.configureBlocking(false)
                              _         <- client.register(selector, Operation.Read)
                            } yield ()
                          } *>
                            IO.whenM(safeStatusCheck(key.isReadable)) {
                              IO.effectSuspendTotal {
                                val sClient = key.channel
                                val client  = sClient.asInstanceOf[zio.nio.core.channels.SocketChannel.NonBlocking]
                                for {
                                  _ <- client.read(buffer)
                                  _ <- buffer.flip
                                  _ <- client.write(buffer)
                                  _ <- buffer.clear
                                  _ <- client.close
                                } yield ()
                              }
                            } *>
                            selector.removeKey(key)
                        }
      } yield ()

    for {
      address <- SocketAddress.inetSocketAddress(0)
      _       <- Selector.make.use { selector =>
                   ServerSocketChannel.open.use { channel =>
                     for {
                       _      <- channel.bind(address)
                       _      <- channel.configureBlocking(false)
                       _      <- channel.register(selector, Operation.Accept)
                       buffer <- Buffer.byte(256)
                       addr   <- channel.localAddress
                       _      <- started.succeed(addr)

                       /*
                  *  we need to run the server loop twice:
                  *  1. to accept the client request
                  *  2. to read from the client channel
                  */
                       _ <- serverLoop(selector, channel, buffer).repeat(Schedule.once)
                     } yield ()
                   }
                 }
    } yield ()
  }

  def client(address: SocketAddress): ZIO[Blocking, Exception, String] = {
    val bytes = Chunk.fromArray("Hello world".getBytes)
    for {
      buffer <- Buffer.byte(bytes)
      text   <- blocking.blocking {
                  SocketChannel.open(address).use { client =>
                    for {
                      _     <- client.write(buffer)
                      _     <- buffer.clear
                      _     <- client.read(buffer)
                      array <- buffer.array
                      text   = byteArrayToString(array)
                      _     <- buffer.clear
                    } yield text
                  }
                }
    } yield text
  }
}
