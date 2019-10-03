package zio.nio.channels

import java.nio.channels.{ CancelledKeyException, SocketChannel => JSocketChannel }

import zio._
import zio.clock.Clock
import zio.nio.channels.SelectionKey.Operation
import zio.nio.{ BaseSpec, Buffer, SocketAddress }
import zio.test._
import zio.test.Assertion._
import SelectorSpecUtils._

object SelectorSpec
    extends BaseSpec(
      suite("SelectorSpec")(
        testM("read/write") {
          for {
            started     <- Promise.make[Nothing, Unit]
            serverFiber <- server(started).fork
            _           <- started.await
            clientFiber <- client.fork
            _           <- serverFiber.join
            message     <- clientFiber.join
          } yield assert(message == "Hello world", isTrue)
        }
      )
    )

object SelectorSpecUtils {

  def byteArrayToString(array: Array[Byte]): String =
    array.takeWhile(_ != 10).map(_.toChar).mkString.trim

  val addressIo = SocketAddress.inetSocketAddress("0.0.0.0", 1111)

  def safeStatusCheck(statusCheck: IO[CancelledKeyException, Boolean]): IO[Nothing, Boolean] =
    statusCheck.either.map(_.getOrElse(false))

  def server(started: Promise[Nothing, Unit]): ZIO[Clock, Exception, Unit] = {
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
      address <- addressIo
      _ <- Selector.make.use { selector =>
            ServerSocketChannel.open.use { channel =>
              for {
                _      <- channel.bind(address)
                _      <- channel.configureBlocking(false)
                _      <- channel.register(selector, Operation.Accept)
                buffer <- Buffer.byte(256)
                _      <- started.succeed(())

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

  def client: IO[Exception, String] =
    for {
      address <- addressIo
      bytes   = Chunk.fromArray("Hello world".getBytes)
      buffer  <- Buffer.byte(bytes)
      text <- SocketChannel.open(address).use { client =>
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
