package zio.nio.core.channels

import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.core._
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }
import zio.{ IO, _ }

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
        def echoServer(started: Promise[Nothing, SocketAddress]): IO[IOException, Unit] =
          for {
            sink <- Buffer.byte(3)
            _    <- DatagramChannel.open.use { server =>
                      for {
                        _          <- server.bindAuto
                        addr       <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                        _          <- started.succeed(addr)
                        retAddress <- server.receive(sink)
                        addr       <- IO.fromOption(retAddress)
                        _          <- sink.flip
                        _          <- server.send(sink, addr)
                      } yield ()
                    }.fork
          } yield ()

        def echoClient(address: SocketAddress): IO[IOException, Boolean] =
          for {
            src    <- Buffer.byte(3)
            result <- DatagramChannel.open.use { client =>
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
        def client(address: SocketAddress): IO[IOException, Unit] = DatagramChannel.open.use(_.connect(address).unit)

        def server(
          address: Option[SocketAddress],
          started: Promise[Nothing, SocketAddress]
        ): IO[Nothing, Fiber[IOException, Unit]] =
          for {
            worker <- DatagramChannel.open.use { server =>
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
