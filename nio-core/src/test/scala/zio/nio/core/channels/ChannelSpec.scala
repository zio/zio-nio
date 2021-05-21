package zio.nio.core.channels

import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.core.{ BaseSpec, Buffer, EffectOps, SocketAddress }
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }
import zio.{ IO, _ }

import java.io.{ EOFException, FileNotFoundException, IOException }

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
            sink <- Buffer.byte(3)
            _    <- AsynchronousServerSocketChannel
                      .open()
                      .use { server =>
                        for {
                          _    <- server.bindAuto()
                          addr <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                          _    <- started.succeed(addr)
                          _    <- server.accept.use { worker =>
                                    worker.read(sink) *>
                                      sink.flip *>
                                      worker.write(sink)
                                  }
                        } yield ()
                      }
                      .fork
          } yield ()

        def echoClient(address: SocketAddress): IO[Exception, Boolean] =
          for {
            src    <- Buffer.byte(3)
            result <- AsynchronousSocketChannel.open().use { client =>
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
          address       <- serverStarted.await
          same          <- echoClient(address)
        } yield assert(same)(isTrue)
      },
      testM("read should fail when connection close") {
        def server(started: Promise[Nothing, SocketAddress]): IO[Exception, Fiber[Exception, Boolean]] =
          for {
            result <- AsynchronousServerSocketChannel
                        .open()
                        .use { server =>
                          for {
                            _      <- server.bindAuto()
                            addr   <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                            _      <- started.succeed(addr)
                            result <- server.accept
                                        .use(worker => worker.readChunk(3) *> worker.readChunk(3) *> ZIO.succeed(false))
                                        .catchSome { case _: java.io.EOFException =>
                                          ZIO.succeed(true)
                                        }
                          } yield result
                        }
                        .fork
          } yield result

        def client(address: SocketAddress): IO[Exception, Unit] =
          for {
            _ <- AsynchronousSocketChannel.open().use { client =>
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
          AsynchronousSocketChannel.open().use {
            _.connect(address)
          }

        def server(
          started: Promise[Nothing, SocketAddress]
        ): Managed[IOException, Fiber[Exception, Unit]] =
          for {
            server <- AsynchronousServerSocketChannel.open()
            _      <- server.bindAuto().toManaged_
            addr   <- server.localAddress.someOrElseM(IO.die(new NoSuchElementException)).toManaged_
            _      <- started.succeed(addr).toManaged_
            worker <- server.accept.unit.fork
          } yield worker

        for {
          serverStarted1 <- Promise.make[Nothing, SocketAddress]
          _              <- server(serverStarted1).use { s1 =>
                              serverStarted1.await.flatMap(client).zipRight(s1.join)
                            }
          serverStarted2 <- Promise.make[Nothing, SocketAddress]
          _              <- server(serverStarted2).use { s2 =>
                              serverStarted2.await.flatMap(client).zipRight(s2.join)
                            }
        } yield assertCompletes
      },
      suite("explicit end-of-stream")(
        testM("converts EOFException to None") {
          assertM(IO.fail(new EOFException).eofCheck.run)(fails(isNone))
        },
        testM("converts non EOFException to Some") {
          val e: IOException = new FileNotFoundException()
          assertM(IO.fail(e).eofCheck.run)(fails(isSome(equalTo(e))))
        },
        testM("passes through success") {
          assertM(IO.succeed(42).eofCheck.run)(succeeds(equalTo(42)))
        }
      )
    )
}
