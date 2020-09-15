package zio.nio.core.channels

import java.io.{ EOFException, FileNotFoundException, IOException }

import zio.nio.core.{ BaseSpec, Buffer, EffectOps, SocketAddress }
import zio.test.{ suite, testM }
import zio.{ IO, _ }
import zio.test._
import zio.test.Assertion._

object ChannelSpec extends BaseSpec {

  override def spec =
    suite("ChannelSpec")(
      testM("read/write") {
        def echoServer(started: Promise[Nothing, SocketAddress]): IO[Exception, Unit] =
          for {
            address <- SocketAddress.inetSocketAddress(0)
            sink    <- Buffer.byte(3)
            _       <- Managed
                         .make(AsynchronousServerSocketChannel())(_.close.orDie)
                         .use { server =>
                           for {
                             _    <- server.bind(address)
                             addr <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                             _    <- started.succeed(addr)
                             _    <- Managed.make(server.accept)(_.close.orDie).use { worker =>
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
            result <- Managed.make(AsynchronousSocketChannel())(_.close.orDie).use { client =>
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
            address <- SocketAddress.inetSocketAddress(0)
            result  <- Managed
                         .make(AsynchronousServerSocketChannel())(_.close.orDie)
                         .use { server =>
                           for {
                             _      <- server.bind(address)
                             addr   <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
                             _      <- started.succeed(addr)
                             result <- Managed
                                         .make(server.accept)(_.close.orDie)
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
            _ <- Managed.make(AsynchronousSocketChannel())(_.close.orDie).use { client =>
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
          for {
            client <- AsynchronousSocketChannel()
            _      <- client.connect(address)
            _      <- client.close
          } yield ()

        def server(
          address: SocketAddress,
          started: Promise[Nothing, SocketAddress]
        ): IO[Exception, Fiber[Exception, Unit]] =
          for {
            server <- AsynchronousServerSocketChannel()
            _      <- server.bind(address)
            addr   <- server.localAddress.flatMap(opt => IO.effect(opt.get).orDie)
            _      <- started.succeed(addr)
            worker <- server.accept
                        .bracket(_.close.ignore *> server.close.ignore)(_ => ZIO.unit)
                        .fork
          } yield worker

        for {
          address       <- SocketAddress.inetSocketAddress(0)
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
