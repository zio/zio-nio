package scalaz.nio

import java.nio.channels.{
  CancelledKeyException,
  SocketChannel => JSocketChannel,
  SelectionKey => JSelectionKey
}

import scalaz.nio.channels._
import scalaz.zio._
import scalaz.zio.clock.Clock
import testz.{ Harness, assert }

object SelectorSuite extends DefaultRuntime {

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(test("read/write") { () =>
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
            _ <- IO.foreach(selectedKeys) {
                  key =>
                    IO.whenM(safeStatusCheck(key.isAcceptable)) {
                      for {
                        clientOpt <- channel.accept
                        client    = clientOpt.get
                        _         <- client.configureBlocking(false)
                        _         <- client.register(selector, JSelectionKey.OP_READ)
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
          address  <- addressIo
          selector <- Selector.make
          channel  <- ServerSocketChannel.open
          _        <- channel.bind(address)
          _        <- channel.configureBlocking(false)
          _        <- channel.register(selector, JSelectionKey.OP_ACCEPT)
          buffer   <- Buffer.byte(256)
          _        <- started.succeed(())

          /*
           *  we need to run the server loop twice:
           *  1. to accept the client request
           *  2. to read from the client channel
           */
          _ <- serverLoop(selector, channel, buffer).repeat(Schedule.once)
          _ <- channel.close
        } yield ()
      }

      def client: IO[Exception, String] =
        for {
          address <- addressIo
          client  <- SocketChannel.open(address)
          bytes   = Chunk.fromArray("Hello world".getBytes)
          buffer  <- Buffer.byte(bytes)
          _       <- client.write(buffer)
          _       <- buffer.clear
          _       <- client.read(buffer)
          array   <- buffer.array
          text    = byteArrayToString(array)

          _ <- buffer.clear
        } yield text

      val testProgram: ZIO[Clock, Exception, Boolean] = for {
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
