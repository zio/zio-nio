package zio.nio.channels

import zio._
import zio.nio.{BaseSpec, Buffer, EffectOps, InetSocketAddress, SocketAddress}
import zio.test.Assertion._
import zio.test._

import java.io.{EOFException, FileNotFoundException, IOException}
import java.nio.channels
import java.{nio => jnio}

object ChannelSpec extends BaseSpec {

  override def spec =
    suite("Channel")(
      test("localAddress") {
        SocketChannel.open.flatMap { con =>
          for {
            _            <- con.bindAuto
            localAddress <- con.localAddress
          } yield assert(localAddress)(isSome(isSubtype[InetSocketAddress](anything)))
        }
      },
      suite("AsynchronousSocketChannel")(
        test("read/write") {
          def echoServer(started: Promise[Nothing, SocketAddress])(implicit trace: Trace): IO[Exception, Unit] =
            for {
              sink <- Buffer.byte(3)
              _ <- ZIO.scoped {
                     AsynchronousServerSocketChannel.open.flatMap { server =>
                       for {
                         _    <- server.bindAuto()
                         addr <- server.localAddress.flatMap(opt => ZIO.attempt(opt.get).orDie)
                         _    <- started.succeed(addr)
                         _ <- ZIO.scoped {
                                server.accept.flatMap { worker =>
                                  worker.read(sink) *>
                                    sink.flip *>
                                    worker.write(sink)
                                }
                              }
                       } yield ()
                     }
                   }.fork
            } yield ()

          def echoClient(address: SocketAddress)(implicit trace: Trace): IO[Exception, Boolean] =
            for {
              src <- Buffer.byte(3)
              result <- ZIO.scoped {
                          AsynchronousSocketChannel.open.flatMap { client =>
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
                        }
            } yield result

          for {
            serverStarted <- Promise.make[Nothing, SocketAddress]
            _             <- echoServer(serverStarted)
            address       <- serverStarted.await
            same          <- echoClient(address)
          } yield assert(same)(isTrue)
        },
        test("read should fail when connection close") {
          def server(started: Promise[Nothing, SocketAddress])(implicit
            trace: Trace
          ): IO[Exception, Fiber[Exception, Boolean]] =
            for {
              result <- ZIO.scoped {
                          AsynchronousServerSocketChannel.open.flatMap { server =>
                            for {
                              _    <- server.bindAuto()
                              addr <- server.localAddress.flatMap(opt => ZIO.attempt(opt.get).orDie)
                              _    <- started.succeed(addr)
                              result <- ZIO.scoped {
                                          server.accept
                                            .flatMap(worker =>
                                              worker.readChunk(3) *> worker.readChunk(3) *> ZIO.succeed(false)
                                            )
                                        }.catchSome { case _: java.io.EOFException =>
                                          ZIO.succeed(true)
                                        }
                            } yield result
                          }
                        }.fork
            } yield result

          def client(address: SocketAddress)(implicit trace: Trace): IO[Exception, Unit] =
            for {
              _ <- ZIO.scoped {
                     AsynchronousSocketChannel.open.flatMap { client =>
                       for {
                         _ <- client.connect(address)
                         _  = client.writeChunk(Chunk.fromArray(Array[Byte](1, 1, 1)))
                       } yield ()
                     }
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
        test("close channel unbind port") {
          def client(address: SocketAddress)(implicit trace: Trace): IO[Exception, Unit] =
            ZIO.scoped {
              AsynchronousSocketChannel.open.flatMap {
                _.connect(address)
              }
            }

          def server(
            started: Promise[Nothing, SocketAddress]
          )(implicit trace: Trace): ZIO[Scope, IOException, Fiber[Exception, Unit]] =
            for {
              server <- AsynchronousServerSocketChannel.open
              _      <- server.bindAuto()
              addr   <- server.localAddress.someOrElseZIO(ZIO.die(new NoSuchElementException))
              _      <- started.succeed(addr)
              worker <- server.accept.unit.forkScoped
            } yield worker

          for {
            serverStarted1 <- Promise.make[Nothing, SocketAddress]
            _ <- ZIO.scoped {
                   server(serverStarted1).flatMap { s1 =>
                     serverStarted1.await.flatMap(client).zipRight(s1.join)
                   }
                 }
            serverStarted2 <- Promise.make[Nothing, SocketAddress]
            _ <- ZIO.scoped {
                   server(serverStarted2).flatMap { s2 =>
                     serverStarted2.await.flatMap(client).zipRight(s2.join)
                   }
                 }
          } yield assertCompletes
        },
        test("read can be interrupted") {
          live {
            ZIO.scoped {
              AsynchronousServerSocketChannel.open
                .tap(_.bindAuto())
                .flatMap { serverChannel =>
                  for {
                    serverAddress <-
                      serverChannel.localAddress.someOrElseZIO(ZIO.dieMessage("Local address must be bound"))
                    promise <- Promise.make[Nothing, Unit]
                    fiber <- ZIO.scoped {
                               AsynchronousSocketChannel.open
                                 .tap(_.connect(serverAddress))
                                 .flatMap(channel => promise.succeed(()) *> channel.readChunk(1))
                             }.fork
                    _    <- promise.await
                    _    <- ZIO.sleep(500.milliseconds)
                    exit <- fiber.interrupt
                  } yield assert(exit)(isInterrupted)
                }
            }
          }
        },
        test("accept can be interrupted") {
          live {
            ZIO.scoped {
              AsynchronousServerSocketChannel.open.tap(_.bindAuto()).flatMap { serverChannel =>
                for {
                  fiber <- ZIO.scoped(serverChannel.accept).fork
                  _     <- ZIO.sleep(500.milliseconds)
                  exit  <- fiber.interrupt
                } yield assert(exit)(isInterrupted)
              }
            }
          }
        }
      ),
      suite("explicit end-of-stream")(
        test("converts EOFException to None") {
          assertZIO(ZIO.fail(new EOFException).eofCheck.exit)(fails(isNone))
        },
        test("converts non EOFException to Some") {
          val e: IOException = new FileNotFoundException()
          assertZIO(ZIO.fail(e).eofCheck.exit)(fails(isSome(equalTo(e))))
        },
        test("passes through success") {
          assertZIO(ZIO.succeed(42).eofCheck.exit)(succeeds(equalTo(42)))
        }
      ),
      suite("blocking operations")(
        test("read can be interrupted") {
          live {
            for {
              promise <- Promise.make[Nothing, Unit]
              fiber <- ZIO.scoped {
                         Pipe.open
                           .flatMap(_.source)
                           .flatMapNioBlockingOps(ops => promise.succeed(()) *> ops.readChunk(1))

                       }.fork
              _    <- promise.await
              _    <- ZIO.sleep(500.milliseconds)
              exit <- fiber.interrupt
            } yield assert(exit)(isInterrupted)
          }
        },
        test("write can be interrupted") {
          val hangingOps: GatheringByteOps = new GatheringByteOps {
            override protected[channels] val channel = new jnio.channels.GatheringByteChannel {

              @volatile private var _closed = false

              private def hang(): Nothing = {
                while (!_closed)
                  Thread.sleep(10L)
                throw new jnio.channels.AsynchronousCloseException()
              }

              override def write(srcs: Array[jnio.ByteBuffer], offset: Int, length: Int): Long = hang()

              override def write(srcs: Array[jnio.ByteBuffer]): Long = hang()

              override def write(src: jnio.ByteBuffer): Int = hang()

              override def isOpen: Boolean = !_closed

              override def close(): Unit = _closed = true
            }
          }
          val hangingChannel = new BlockingChannel {
            override type BlockingOps = GatheringByteOps

            override def flatMapBlocking[R, E >: IOException, A](
              f: GatheringByteOps => ZIO[R, E, A]
            )(implicit trace: Trace): ZIO[R, E, A] = nioBlocking(f(hangingOps))

            override protected val channel: channels.Channel = hangingOps.channel
          }

          live {
            for {
              promise <- Promise.make[Nothing, Unit]
              fiber <-
                hangingChannel
                  .flatMapBlocking(ops => promise.succeed(()) *> ops.writeChunk(Chunk.single(42.toByte)))
                  .fork
              _    <- promise.await
              _    <- ZIO.sleep(500.milliseconds)
              exit <- fiber.interrupt
            } yield assert(exit)(isInterrupted)
          }
        },
        test("accept can be interrupted") {
          live {
            ZIO.scoped {
              ServerSocketChannel.open.tap(_.bindAuto()).flatMap { serverChannel =>
                for {
                  promise <- Promise.make[Nothing, Unit]
                  fiber   <- serverChannel.flatMapBlocking(ops => promise.succeed(()) *> ZIO.scoped(ops.accept)).fork
                  _       <- promise.await
                  _       <- ZIO.sleep(500.milliseconds)
                  exit    <- fiber.interrupt
                } yield assert(exit)(isInterrupted)
              }
            }
          }
        }
      )
    )
}
