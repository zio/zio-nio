package scalaz.nio

import scalaz.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import scalaz.zio.{ IO, RTS }
import testz.{ Harness, assert }

object ChannelSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(test("read/write") { () =>
      val inetAddress = InetAddress.localHost
        .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13370))

      def echoServer: IO[Exception, Unit] =
        for {
          address <- inetAddress
          sink    <- Buffer.byte(3)
          server  <- AsynchronousServerSocketChannel()
          _       <- server.bind(address)
          worker  <- server.accept
          _       <- worker.read(sink)
          _       <- sink.flip
          _       <- worker.write(sink)
          _       <- worker.close
          _       <- server.close
        } yield ()

      def echoClient: IO[Exception, Boolean] =
        for {
          address  <- inetAddress
          src      <- Buffer.byte(3)
          client   <- AsynchronousSocketChannel()
          _        <- client.connect(address)
          sent     <- src.array
          _        = sent.update(0, 1)
          _        <- client.write(src)
          _        <- src.flip
          _        <- client.read(src)
          received <- src.array
          _        <- client.close
        } yield sent.sameElements(received)

      val testProgram: IO[Exception, Boolean] = for {
        serverFiber <- echoServer.fork
        clientFiber <- echoClient.fork
        _           <- serverFiber.join
        same        <- clientFiber.join
      } yield same

      assert(unsafeRun(testProgram))
    })
  }
}
