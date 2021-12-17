package zio.nio.channels

import zio._
import zio.Clock
import zio.nio._
import zio.Random
import zio.test.Assertion._
import zio.test._
import zio.test.{Live, TestClock, TestConsole, TestRandom, TestSystem}

import java.io.IOException

object DatagramChannelSpec extends BaseSpec {

  override def spec: Spec[
    Annotations with Live with Sized with TestClock with TestConfig with TestConsole with TestRandom with TestSystem with Clock with zio.Console with zio.System with Random,
    TestFailure[Any],
    TestSuccess
  ] =
    suite("DatagramChannelSpec")(
      test("read/write") {
        def echoServer(started: Promise[Nothing, SocketAddress]): UIO[Unit] =
          for {
            sink <- Buffer.byte(3)
            _ <- DatagramChannel.open.useNioBlocking { (server, ops) =>
                   for {
                     _    <- server.bindAuto
                     addr <- server.localAddress.someOrElseM(ZIO.dieMessage("Must have local address"))
                     _    <- started.succeed(addr)
                     addr <- ops.receive(sink)
                     _    <- sink.flip
                     _    <- ops.send(sink, addr)
                   } yield ()
                 }.fork
          } yield ()

        def echoClient(address: SocketAddress): IO[IOException, Boolean] =
          for {
            src <- Buffer.byte(3)
            result <- DatagramChannel.open.useNioBlockingOps { client =>
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
      test("close channel unbind port") {
        def client(address: SocketAddress): IO[IOException, Unit] =
          DatagramChannel.open.useNioBlockingOps(_.connect(address).unit)

        def server(
          address: Option[SocketAddress],
          started: Promise[Nothing, SocketAddress]
        ): UIO[Fiber[IOException, Unit]] =
          for {
            worker <- DatagramChannel.open.useNioBlocking { (server, _) =>
                        for {
                          _    <- server.bind(address)
                          addr <- server.localAddress.someOrElseZIO(ZIO.dieMessage("Local address must be bound"))
                          _    <- started.succeed(addr)
                        } yield ()
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
