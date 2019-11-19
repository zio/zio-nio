package zio.nio.channels

import zio.nio.{ BaseSpec, Buffer, InetAddress, InetSocketAddress, SocketAddress }
import zio.test.{ suite, testM }
import zio.{ IO, _ }
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.ignore

object ChannelSpec
    extends BaseSpec(
      suite("ChannelSpec")(
        testM("read/write") {
          val inetAddress: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
            .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13370))

          def echoServer(promise: Promise[Nothing, Unit]): IO[Exception, Unit] =
            for {
              address <- inetAddress
              sink    <- Buffer.byte(3)
              _ <- AsynchronousServerSocketChannel().use { server =>
                    for {
                      _ <- server.bind(address)
                      _ <- promise.succeed(())
                      _ <- server.accept.use { worker =>
                            worker.readBuffer(sink) *>
                              sink.flip *>
                              worker.writeBuffer(sink)
                          }
                    } yield ()
                  }.fork
            } yield ()

          def echoClient: IO[Exception, Boolean] =
            for {
              address <- inetAddress
              src     <- Buffer.byte(3)
              result <- AsynchronousSocketChannel().use { client =>
                         for {
                           _        <- client.connect(address)
                           sent     <- src.array
                           _        = sent.update(0, 1)
                           _        <- client.writeBuffer(src)
                           _        <- src.flip
                           _        <- client.readBuffer(src)
                           received <- src.array
                         } yield sent.sameElements(received)
                       }
            } yield result

          for {
            serverStarted <- Promise.make[Nothing, Unit]
            _             <- echoServer(serverStarted)
            _             <- serverStarted.await
            same          <- echoClient
          } yield assert(same, isTrue)
        },
        testM("read should fail when connection close") {
          val inetAddress: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
            .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13372))

          def server(started: Promise[Nothing, Unit]): IO[Exception, Fiber[Exception, Boolean]] =
            for {
              address <- inetAddress
              result <- AsynchronousServerSocketChannel().use { server =>
                         for {
                           _ <- server.bind(address)
                           _ <- started.succeed(())
                           result <- server.accept
                                      .use { worker =>
                                        worker.read(3) *> worker.read(3) *> ZIO.succeed(false)
                                      }
                                      .catchAll {
                                        case ex: java.io.IOException if ex.getMessage == "Connection reset by peer" =>
                                          ZIO.succeed(true)
                                      }
                         } yield result
                       }.fork
            } yield result

          def client: IO[Exception, Unit] =
            for {
              address <- inetAddress
              _ <- AsynchronousSocketChannel().use { client =>
                    for {
                      _ <- client.connect(address)
                      _ = client.write(Chunk.fromArray(Array[Byte](1, 1, 1)))
                    } yield ()
                  }
            } yield ()

          for {
            serverStarted <- Promise.make[Nothing, Unit]
            serverFiber   <- server(serverStarted)
            _             <- serverStarted.await
            _             <- client
            same          <- serverFiber.join
          } yield assert(same, isTrue)
        },
        testM("close channel unbind port") {
          val inetAddress: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
            .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13376))

          def client: IO[Exception, Unit] =
            for {
              address <- inetAddress
              _ <- AsynchronousSocketChannel().use { client =>
                    client.connect(address).unit
                  }
            } yield ()

          def server(started: Promise[Nothing, Unit]): IO[Exception, Fiber[Exception, Unit]] =
            for {
              address <- inetAddress
              worker <- AsynchronousServerSocketChannel().use { server =>
                         for {
                           _ <- server.bind(address)
                           _ <- started.succeed(())
                           worker <- server.accept.use { _ =>
                                      ZIO.unit
                                    }
                         } yield worker
                       }.fork
            } yield worker

          for {
            serverStarted <- Promise.make[Nothing, Unit]
            s1            <- server(serverStarted)
            _             <- serverStarted.await
            _             <- client
            _             <- s1.join
            serverStarted <- Promise.make[Nothing, Unit]
            s2            <- server(serverStarted)
            _             <- serverStarted.await
            _             <- client
            _             <- s2.join
          } yield assert(true, isTrue)
        },
        // this would best be tagged as an regression test. for now just run manually when suspicious.
        testM("accept should not leak resources") {
          val server = for {
            addr    <- SocketAddress.inetSocketAddress(8081).toManaged_
            channel <- AsynchronousServerSocketChannel()
            _       <- channel.bind(addr).toManaged_
            _       <- AsynchronousSocketChannel().use(channel => channel.connect(addr)).forever.toManaged_.fork
          } yield channel
          val interruptAccept = server.use(
            _.accept
              .use(_ => ZIO.interrupt)
              .catchSomeCause { case Cause.Interrupt(_) => ZIO.unit }
              .repeat(Schedule.recurs(20000))
          )
          assertM(interruptAccept.run, succeeds(equalTo(20000)))
        } @@ ignore
      )
    )
