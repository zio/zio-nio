package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.CancelledKeyException

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.core.channels.SelectionKey.Operation
import zio.nio.core.{ BaseSpec, Buffer, ByteBuffer, SocketAddress }
import zio.test.Assertion._
import zio.test._

object SelectorSpec extends BaseSpec {

  override def spec =
    suite("SelectorSpec")(
      testM("read/write") {
        for {
          started     <- Promise.make[Nothing, SocketAddress]
          serverFiber <- server(started).useNow.fork
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

  def server(started: Promise[Nothing, SocketAddress]): ZManaged[Clock with Blocking, Exception, Unit] = {
    def serverLoop(
      scope: Managed.Scope,
      selector: Selector,
      buffer: ByteBuffer
    ): ZIO[Blocking, Exception, Unit] =
      for {
        _            <- selector.select
        selectedKeys <- selector.selectedKeys
        _            <- IO.foreach_(selectedKeys) { key =>
                          key.matchChannel { readyOps =>
                            {
                              case channel: ServerSocketChannel if readyOps(Operation.Accept) =>
                                for {
                                  scopeResult     <- scope(channel.useNonBlockingManaged(_.accept))
                                  (_, maybeClient) = scopeResult
                                  _               <- IO.whenCase(maybeClient) {
                                                       case Some(client) =>
                                                         client.configureBlocking(false) *> client.register(selector, Operation.Read)
                                                     }
                                } yield ()
                              case channel: SocketChannel if readyOps(Operation.Read)         =>
                                channel.useNonBlocking { client =>
                                  for {
                                    _ <- client.read(buffer)
                                    _ <- buffer.flip
                                    _ <- client.write(buffer)
                                    _ <- buffer.clear
                                    _ <- channel.close
                                  } yield ()
                                }
                            }
                          } *> selector.removeKey(key)
                        }
      } yield ()

    for {
      scope    <- Managed.scope
      selector <- Selector.open
      channel  <- ServerSocketChannel.open
      _        <- Managed.fromEffect {
                    for {
                      _      <- channel.bindAuto()
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
                      _ <- serverLoop(scope, selector, buffer).repeat(Schedule.once)
                    } yield ()
                  }
    } yield ()
  }

  def client(address: SocketAddress): ZIO[Blocking, IOException, String] = {
    val bytes = Chunk.fromArray("Hello world".getBytes)
    for {
      buffer <- Buffer.byte(bytes)
      text   <- SocketChannel.open(address).useNioBlockingOps { client =>
                  for {
                    _     <- client.write(buffer)
                    _     <- buffer.clear
                    _     <- client.read(buffer)
                    array <- buffer.array
                    text   = byteArrayToString(array)
                    _     <- buffer.clear
                  } yield text
                }
    } yield text
  }
}
