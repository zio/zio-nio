package zio.nio.channels

import testz.{ Harness, assert }
import zio.nio.{ Buffer, InetAddress, SocketAddress }
import zio.{ DefaultRuntime, IO, _ }

object ChannelSuite extends DefaultRuntime {

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("read/write") { () =>
        val inetAddress = InetAddress.localHost
          .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13370))

        def echoServer: IO[Exception, Unit] =
          for {
            address <- inetAddress
            sink    <- Buffer.byte(3)
            _ <- AsynchronousServerSocketChannel().use { server =>
                  for {
                    _ <- server.bind(address)
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

        val testProgram: IO[Exception, Boolean] = for {
          _    <- echoServer
          same <- echoClient
        } yield same

        assert(unsafeRun(testProgram))
      },
      test("read should fail when connection close") { () =>
        val inetAddress = InetAddress.localHost
          .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13372))

        def server: IO[Exception, Fiber[Exception, Boolean]] =
          for {
            address <- inetAddress
            result <- AsynchronousServerSocketChannel().use { server =>
                       for {
                         _ <- server.bind(address)
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

        val testProgram: IO[Exception, Boolean] = for {
          serverFiber <- server
          _           <- client
          same        <- serverFiber.join
        } yield same

        assert(unsafeRun(testProgram))
      },
      test("close channel unbind port") { () =>
        val inetAddress = InetAddress.localHost
          .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13376))

        def client: IO[Exception, Unit] =
          for {
            address <- inetAddress
            _ <- AsynchronousSocketChannel().use { client =>
                  client.connect(address).unit
                }
          } yield ()

        def server: IO[Exception, Fiber[Exception, Unit]] =
          for {
            address <- inetAddress
            worker <- AsynchronousServerSocketChannel().use { server =>
                       for {
                         _ <- server.bind(address)
                         worker <- server.accept.use { _ =>
                                    ZIO.unit
                                  }
                       } yield worker
                     }.fork
          } yield worker

        def testProgram: IO[Exception, Boolean] =
          for {
            s1 <- server
            _  <- client
            _  <- s1.join
            s2 <- server
            _  <- client
            _  <- s2.join
          } yield true

        assert(unsafeRun(testProgram))
      }
    )

  }
}
