package zio.nio.channels

import java.nio.channels.{ CancelledKeyException, SocketChannel => JSocketChannel }

import testz.{ Harness, assert }
import zio._
import zio.console._
import zio.clock.Clock
import zio.nio.channels.SelectionKey.Operation
import zio.nio.{ Buffer, SocketAddress }

object SelectorSuite extends DefaultRuntime {

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(test("read/write") { () =>
      def byteArrayToString(array: Array[Byte]): String =
        array.takeWhile(_ != 10).map(_.toChar).mkString.trim

      val addressIo = SocketAddress.inetSocketAddress("0.0.0.0", 1111)

      def safeStatusCheck(statusCheck: IO[CancelledKeyException, Boolean]): IO[Nothing, Boolean] =
        statusCheck.either.map(_.getOrElse(false))

      def server(started: Promise[Nothing, Unit]): ZIO[Clock with Console, Exception, Unit] = {
        def serverLoop(
          selector: Selector,
          channel: ServerSocketChannel,
          buffer: Buffer[Byte]
        ): ZIO[Console, Exception, Unit] =
          for {
            _            <- putStrLn("server loop")
            _            <- selector.select
            selectedKeys <- selector.selectedKeys
            _ <- ZIO.foreach(selectedKeys) {
                  key =>
                    ZIO.whenM(safeStatusCheck(key.isAcceptable)) {
                      for {
                        clientOpt <- channel.accept
                        client    = clientOpt.get
                        _         <- client.configureBlocking(false)
                        _         <- client.register(selector, Operation.Read)
                      } yield ()
                    } *>
                      ZIO.whenM(safeStatusCheck(key.isReadable)) {
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
          _       <- putStrLn("starting server")
          address <- addressIo
          _ <- Selector.make.use {
                selector =>
                  ServerSocketChannel.open.use {
                    channel =>
                      for {
                        _      <- channel.bind(address)
                        _      <- putStrLn(s"bound to $address")
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

      def client: ZIO[Console, Exception, String] =
        for {
          _       <- putStrLn("starting client")
          address <- addressIo
          bytes   = Chunk.fromArray("Hello world".getBytes)
          buffer  <- Buffer.byte(bytes)
          text <- SocketChannel.open(address).use { client =>
                   for {
                     _     <- putStrLn("client socket open")
                     _     <- client.write(buffer)
                     _     <- buffer.clear
                     _     <- client.read(buffer)
                     array <- buffer.array
                     text  = byteArrayToString(array)
                     _     <- buffer.clear
                   } yield text
                 }
          _ <- putStrLn("client finished")
        } yield text

      val testProgram: ZIO[Clock with Console, Exception, Boolean] = for {
        started     <- Promise.make[Nothing, Unit]
        serverFiber <- server(started).fork
        _           <- started.await
        clientFiber <- client.fork
        _           <- serverFiber.join
        message     <- clientFiber.join
      } yield message == "Hello world"

      assert(unsafeRun(testProgram))
    })
  }
}
