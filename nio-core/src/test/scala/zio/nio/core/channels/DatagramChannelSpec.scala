package zio.nio.core.channels

import zio.nio.core._
import zio.test.Assertion._
import zio.test.{ suite, testM, _ }
import zio.{ IO, _ }

object DatagramChannelSpec extends BaseSpec {

  override def spec =
    suite("DatagramChannelSpec")(
      testM("read/write") {
        def echoServer(started: Promise[Nothing, SocketAddress]): IO[Exception, Unit] =
          for {
            address <- SocketAddress.inetSocketAddress(0)
            sink    <- Buffer.byte(3)
            _       <- Managed
                         .make(DatagramChannel.open)(_.close.orDie)
                         .use { server =>
                           for {
                             _          <- server.bind(Some(address))
                             addr       <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                             _          <- started.succeed(addr)
                             retAddress <- server.receive(sink)
                             addr       <- IO.fromOption(retAddress)
                             _          <- sink.flip
                             _          <- server.send(sink, addr)
                           } yield ()
                         }
                         .fork
          } yield ()

        def echoClient(address: SocketAddress): IO[Exception, Boolean] =
          for {
            src    <- Buffer.byte(3)
            result <- Managed.make(DatagramChannel.open)(_.close.orDie).use { client =>
                        for {
                          _        <- client.connect(address)
                          sent     <- src.array
                          _         = sent.update(0, 1)
                          _        <- client.send(src, address)
                          _        <- src.flip
                          _        <- client.read(src)
                          received <- src.array
                        } yield sent.sameElements(received)
                      }
          } yield result

        for {
          serverStarted <- Promise.make[Nothing, SocketAddress]
          _             <- echoServer(serverStarted)
          addr          <- serverStarted.await
          same          <- echoClient(addr)
        } yield assert(same)(isTrue)
      },
      testM("close channel unbind port") {
        def client(address: SocketAddress): IO[Exception, Unit] =
          Managed.make(DatagramChannel.open)(_.close.orDie).use(_.connect(address).unit)

        def server(
          address: SocketAddress,
          started: Promise[Nothing, SocketAddress]
        ): IO[Exception, Fiber[Exception, Unit]] =
          for {
            worker <- Managed
                        .make(DatagramChannel.open)(_.close.orDie)
                        .use { server =>
                          for {
                            _    <- server.bind(Some(address))
                            addr <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                            _    <- started.succeed(addr)
                          } yield ()
                        }
                        .fork
          } yield worker

        for {
          address        <- SocketAddress.inetSocketAddress(0)
          serverStarted  <- Promise.make[Nothing, SocketAddress]
          s1             <- server(address, serverStarted)
          addr           <- serverStarted.await
          _              <- client(addr)
          _              <- s1.join
          serverStarted2 <- Promise.make[Nothing, SocketAddress]
          s2             <- server(addr, serverStarted2)
          _              <- serverStarted2.await
          _              <- client(addr)
          _              <- s2.join
        } yield assertCompletes
      }
    )
}
