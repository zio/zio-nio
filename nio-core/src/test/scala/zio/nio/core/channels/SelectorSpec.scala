package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.CancelledKeyException

import zio._
import zio.blocking.Blocking
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

  def server(started: Promise[Nothing, SocketAddress]): IO[IOException, Unit] = {
    def serverLoop(
      selector: Selector,
      buffer: ByteBuffer
    ): IO[IOException, Unit] =
      for {
        _            <- selector.select
        selectedKeys <- selector.selectedKeys
        _            <- IO.foreach_(selectedKeys) { key =>
                          key.matchChannel { readyOps =>
                            {
                              case channel: ServerSocketChannel.NonBlocking if readyOps(Operation.Accept) =>
                                for {
                                  client <- channel.acceptNonBlocking.someOrElseM(IO.dieMessage("Nothing to accept!"))
                                  _      <- client.register(selector, Operation.Read)
                                } yield ()
                              case client: SocketChannel.NonBlocking if readyOps(Operation.Read)          =>
                                for {
                                  _     <- client.read(buffer)
                                  array <- buffer.array
                                  text   = byteArrayToString(array)
                                  _     <- buffer.flip
                                  _     <- client.write(buffer)
                                  _     <- buffer.clear
                                  _     <- client.close
                                } yield ()
                            }
                          } *> selector.removeKey(key)
                        }
      } yield ()

    val managed = for {
      address  <- SocketAddress.inetSocketAddress(0).toManaged_
      selector <- Selector.make.toManagedNio
      channel  <- ServerSocketChannel.NonBlocking.open.toManagedNio
      _        <- ZManaged.fromEffect {
                    for {
                      _      <- channel.bind(address)
                      _      <- channel.register(selector, Operation.Accept)
                      buffer <- Buffer.byte(256)
                      addr   <- channel.localAddress.someOrElseM(IO.dieMessage("Address not bound"))
                      _      <- started.succeed(addr)

                      /*
                *  we need to run the server loop twice:
                *  1. to accept the client request
                *  2. to read from the client channel
                */
                      _ <- serverLoop(selector, buffer).repeat(Schedule.once)
                    } yield ()
                  }
    } yield ()
    managed.useNow
  }

  def client(address: SocketAddress): ZIO[Blocking, IOException, String] = {
    val bytes = Chunk.fromArray("Hello world".getBytes)
    for {
      buffer <- Buffer.byte(bytes)
      text   <- SocketChannel.Blocking.open(address).bracketNio { client =>
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
