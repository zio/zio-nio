package zio.nio.channels

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio._
import zio.nio.core.{ Buffer, InetSocketAddress, SocketAddress }
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }

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
        def echoServer(started: Promise[Nothing, SocketAddress]): IO[Exception, Unit] =
          for {
            address <- InetSocketAddress.wildCard(0)
            sink    <- Buffer.byte(3)
            _       <- DatagramChannel
                         .bind(Some(address))
                         .use { server =>
                           for {
                             addr       <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                             _          <- started.succeed(addr)
                             retAddress <- server.receive(sink)
                             addr       <- ZIO.fromOption(retAddress)
                             _          <- sink.flip
                             _          <- server.send(sink, addr)
                           } yield ()
                         }
                         .fork
          } yield ()

        def echoClient(address: SocketAddress): IO[Exception, Boolean] =
          for {
            src    <- Buffer.byte(3)
            result <- DatagramChannel.connect(address).use { client =>
                        for {
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
        def client(address: SocketAddress): IO[Exception, Unit] = DatagramChannel.connect(address).use_(UIO.unit)

        def server(
          address: SocketAddress,
          started: Promise[Nothing, SocketAddress]
        ): IO[Exception, Fiber[Exception, Unit]] =
          for {
            worker <- DatagramChannel
                        .bind(Some(address))
                        .use { server =>
                          for {
                            addr <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                            _    <- started.succeed(addr)
                          } yield ()
                        }
                        .fork
          } yield worker

        for {
          address       <- InetSocketAddress.wildCard(0)
          serverStarted <- Promise.make[Nothing, SocketAddress]
          s1            <- server(address, serverStarted)
          addr          <- serverStarted.await
          _             <- client(addr)
          _             <- s1.join
          serverStarted <- Promise.make[Nothing, SocketAddress]
          s2            <- server(addr, serverStarted)
          _             <- serverStarted.await
          _             <- client(addr)
          _             <- s2.join
        } yield assertCompletes
      }
    )
}
