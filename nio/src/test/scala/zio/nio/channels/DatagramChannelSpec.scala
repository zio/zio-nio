package zio.nio.channels

import zio._
import zio.nio._
import zio.test.Assertion._
import zio.test._

import java.io.IOException

object DatagramChannelSpec extends BaseSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DatagramChannelSpec")(
      test("read/write") {
        def echoServer(started: Promise[Nothing, SocketAddress])(implicit trace: Trace): UIO[Unit] =
          for {
            sink <- Buffer.byte(3)
            _ <- ZIO.scoped {
                   DatagramChannel.open.flatMapNioBlocking { (server, ops) =>
                     for {
                       _    <- server.bindAuto
                       addr <- server.localAddress.someOrElseZIO(ZIO.dieMessage("Must have local address"))
                       _    <- started.succeed(addr)
                       addr <- ops.receive(sink)
                       _    <- sink.flip
                       _    <- ops.send(sink, addr)
                     } yield ()
                   }
                 }.fork
          } yield ()

        def echoClient(address: SocketAddress)(implicit trace: Trace): IO[IOException, Boolean] =
          for {
            src <- Buffer.byte(3)
            result <- ZIO.scoped {
                        DatagramChannel.open.flatMapNioBlockingOps { client =>
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
                      }
          } yield result

        for {
          serverStarted <- Promise.make[Nothing, SocketAddress]
          _             <- echoServer(serverStarted)
          addr          <- serverStarted.await
          same          <- echoClient(addr)
        } yield assert(same)(isTrue)
      },
      test("close channel unbind port") {
        def client(address: SocketAddress)(implicit trace: Trace): IO[IOException, Unit] =
          ZIO.scoped {
            DatagramChannel.open.flatMapNioBlockingOps(_.connect(address).unit)
          }

        def server(
          address: Option[SocketAddress],
          started: Promise[Nothing, SocketAddress]
        )(implicit trace: Trace): UIO[Fiber[IOException, Unit]] =
          for {
            worker <- ZIO.scoped {
                        DatagramChannel.open.flatMapNioBlocking { (server, _) =>
                          for {
                            _    <- server.bind(address)
                            addr <- server.localAddress.someOrElseZIO(ZIO.dieMessage("Local address must be bound"))
                            _    <- started.succeed(addr)
                          } yield ()
                        }
                      }.fork
          } yield worker

        for {
          serverStarted  <- Promise.make[Nothing, SocketAddress]
          s1             <- server(None, serverStarted)
          addr           <- serverStarted.await
          _              <- client(addr)
          _              <- s1.join
          serverStarted2 <- Promise.make[Nothing, SocketAddress]
          s2             <- server(Some(addr), serverStarted2)
          _              <- serverStarted2.await
          _              <- client(addr)
          _              <- s2.join
        } yield assertCompletes
      }
    )
}
