package zio.nio.channels

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio._
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{Live, TestClock, TestConsole, TestRandom, TestSystem}

import java.io.IOException

object DatagramChannelSpec extends BaseSpec {

  override def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ] with Has[Blocking.Service], TestFailure[Any], TestSuccess] =
    suite("DatagramChannelSpec")(
      testM("read/write") {
        def echoServer(started: Promise[Nothing, SocketAddress]): ZIO[Blocking, Nothing, Unit] =
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

        def echoClient(address: SocketAddress): ZIO[Blocking, IOException, Boolean] =
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
      testM("close channel unbind port") {
        def client(address: SocketAddress): ZIO[Blocking, IOException, Unit] =
          DatagramChannel.open.useNioBlockingOps(_.connect(address).unit)

        def server(
          address: Option[SocketAddress],
          started: Promise[Nothing, SocketAddress]
        ): ZIO[Blocking, Nothing, Fiber[IOException, Unit]] =
          for {
            worker <- DatagramChannel.open.useNioBlocking { (server, _) =>
                        for {
                          _    <- server.bind(address)
                          addr <- server.localAddress.someOrElseM(ZIO.dieMessage("Local address must be bound"))
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
