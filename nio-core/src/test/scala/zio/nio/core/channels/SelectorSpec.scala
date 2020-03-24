package zio.nio.core.channels

import java.nio.channels.{ CancelledKeyException, SocketChannel => JSocketChannel }

import zio._
import zio.clock.Clock
import zio.nio.core.channels.SelectionKey.Operation
import zio.nio.core.{ BaseSpec, Buffer, SocketAddress }
import zio.test._
import zio.test.Assertion._

object SelectorSpec extends BaseSpec {

  override def spec = suite("SelectorSpec")(
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

  def server(started: Promise[Nothing, SocketAddress]): ZIO[Clock, Exception, Unit] = {
    def serverLoop(
      selector: Selector,
      channel: ServerSocketChannel,
      buffer: Buffer[Byte]
    ): IO[Exception, Unit] =
      for {
        _            <- selector.select
        selectedKeys <- selector.selectedKeys
        _ <- IO.foreach(selectedKeys) { key =>
              IO.whenM(safeStatusCheck(key.isAcceptable)) {
                for {
                  clientOpt <- channel.accept
                  client    = clientOpt.get
                  _         <- client.configureBlocking(false)
                  _         <- client.register(selector, Operation.Read)
                } yield ()
              } *>
                IO.whenM(safeStatusCheck(key.isReadable)) {
                  for {
                    sClient <- key.channel
                    client  = new SocketChannel(sClient.asInstanceOf[JSocketChannel])
                    _       <- client.read(buffer)
                    array   <- buffer.array
                    text    = byteArrayToString(array)
                    _       <- buffer.flip
                    _       <- client.write(buffer)
                    _       <- buffer.clear
                    _       <- client.close
                  } yield ()
                } *>
                selector.removeKey(key)
            }
      } yield ()

    for {
      address <- SocketAddress.inetSocketAddress(0)
      _ <- Managed.make(Selector.make)(_.close.orDie).use { selector =>
            Managed.make(ServerSocketChannel.open)(_.close.orDie).use { channel =>
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

  def client(address: SocketAddress): IO[Exception, String] = {
    val bytes = Chunk.fromArray("Hello world".getBytes)
    for {
      buffer <- Buffer.byte(bytes)
      text <- Managed.make(SocketChannel.open(address))(_.close.orDie).use { client =>
               for {
                 _     <- client.write(buffer)
                 _     <- buffer.clear
                 _     <- client.read(buffer)
                 array <- buffer.array
                 text  = byteArrayToString(array)
                 _     <- buffer.clear
               } yield text
             }
    } yield text
  }
}
