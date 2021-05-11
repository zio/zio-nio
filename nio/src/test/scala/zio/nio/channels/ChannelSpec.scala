package zio.nio.channels

import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.BaseSpec
import zio.nio.core.{ Buffer, InetSocketAddress, SocketAddress }
import zio.random.Random
import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }
import zio.{ IO, _ }

object ChannelSpec extends BaseSpec {

  override def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ] with Has[Blocking.Service], TestFailure[Any], TestSuccess] =
    suite("ChannelSpec")(
      testM("read/write") {
        def echoServer(started: Promise[Nothing, SocketAddress]): IO[Exception, Unit] =
          for {
            address <- InetSocketAddress.wildCard(0)
            sink    <- Buffer.byte(3)
            _       <- AsynchronousServerSocketChannel().use { server =>
                         for {
                           _    <- server.bindTo(address)
                           addr <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                           _    <- started.succeed(addr)
                           _    <- server.accept.use { worker =>
                                     worker.read(sink) *>
                                       sink.flip *>
                                       worker.write(sink)
                                   }
                         } yield ()
                       }.fork
          } yield ()

        def echoClient(address: SocketAddress): IO[Exception, Boolean] =
          for {
            src    <- Buffer.byte(3)
            result <- AsynchronousSocketChannel().use { client =>
                        for {
                          _        <- client.connect(address)
                          sent     <- src.array
                          _         = sent.update(0, 1)
                          _        <- client.write(src)
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
      testM("read should fail when connection close") {
        def server(started: Promise[Nothing, SocketAddress]): IO[Exception, Fiber[Exception, Boolean]] =
          for {
            address <- InetSocketAddress.wildCard(0)
            result  <- AsynchronousServerSocketChannel().use { server =>
                         for {
                           _      <- server.bindTo(address)
                           addr   <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                           _      <- started.succeed(addr)
                           result <- server.accept
                                       .use(worker => worker.readChunk(3) *> worker.readChunk(3) *> ZIO.succeed(false))
                                       .catchSome { case _: java.io.EOFException =>
                                         ZIO.succeed(true)
                                       }
                         } yield result
                       }.fork
          } yield result

        def client(address: SocketAddress): IO[Exception, Unit] =
          for {
            _ <- AsynchronousSocketChannel().use { client =>
                   for {
                     _ <- client.connect(address)
                     _  = client.writeChunk(Chunk.fromArray(Array[Byte](1, 1, 1)))
                   } yield ()
                 }
          } yield ()

        for {
          serverStarted <- Promise.make[Nothing, SocketAddress]
          serverFiber   <- server(serverStarted)
          addr          <- serverStarted.await
          _             <- client(addr)
          same          <- serverFiber.join
        } yield assert(same)(isTrue)
      },
      testM("close channel unbind port") {
        def client(address: SocketAddress): IO[Exception, Unit] =
          AsynchronousSocketChannel().use(_.connect(address).unit)

        def server(
          address: SocketAddress,
          started: Promise[Nothing, SocketAddress]
        ): IO[Exception, Fiber[Exception, Unit]] =
          for {
            worker <- AsynchronousServerSocketChannel().use { server =>
                        for {
                          _      <- server.bindTo(address)
                          addr   <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                          _      <- started.succeed(addr)
                          worker <- server.accept.use(_ => ZIO.unit)
                        } yield worker
                      }.fork
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
      },
      testM("accept should be interruptible") {
        AsynchronousServerSocketChannel().use { server =>
          for {
            addr   <- InetSocketAddress.wildCard(0)
            _      <- server.bindTo(addr)
            fiber  <- server.accept.useNow.fork
            _      <- fiber.interrupt
            result <- fiber.await
          } yield assert(result)(isInterrupted)
        }
      },
      // this would best be tagged as an regression test. for now just run manually when suspicious.
      testM("accept should not leak resources") {
        val server          = for {
          addr    <- InetSocketAddress.wildCard(8081).toManaged_
          channel <- AsynchronousServerSocketChannel()
          _       <- channel.bindTo(addr).toManaged_
          _       <- AsynchronousSocketChannel().use(channel => channel.connect(addr)).forever.toManaged_.fork
        } yield channel
        val interruptAccept = server.use(
          _.accept
            .use(_ => ZIO.interrupt)
            .catchSomeCause { case Cause.Interrupt(_) => ZIO.unit }
            .repeat(Schedule.recurs(20000))
        )
        assertM(interruptAccept.run)(succeeds(equalTo(20000L)))
      } @@ ignore
    )
}
