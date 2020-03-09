package zio.nio.core.channels

import zio.nio.core.{ BaseSpec, Buffer, InetAddress, InetSocketAddress, SocketAddress }
import zio.test.{ suite, testM }
import zio.{ IO, _ }
import zio.test._
import zio.test.Assertion._

object ChannelSpec extends BaseSpec {

  override def spec = suite("ChannelSpec")(
    testM("read/write") {
      val inetAddress: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
        .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13370))

      def echoServer(promise: Promise[Nothing, Unit]): IO[Exception, Unit] =
        for {
          address <- inetAddress
          sink    <- Buffer.byte(3)
          _ <- Managed
                .make(AsynchronousServerSocketChannel())(_.close.orDie)
                .use { server =>
                  for {
                    _ <- server.bind(address)
                    _ <- promise.succeed(())
                    _ <- Managed.make(server.accept)(_.close.orDie).use { worker =>
                          worker.readBuffer(sink) *>
                            sink.flip *>
                            worker.writeBuffer(sink)
                        }
                  } yield ()
                }
                .fork
        } yield ()

      def echoClient: IO[Exception, Boolean] =
        for {
          address <- inetAddress
          src     <- Buffer.byte(3)
          result <- Managed.make(AsynchronousSocketChannel())(_.close.orDie).use { client =>
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
      } yield assert(same)(isTrue)
    },
    testM("read should fail when connection close") {
      val inetAddress: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
        .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13372))

      def server(started: Promise[Nothing, Unit]): IO[Exception, Fiber[Exception, Boolean]] =
        for {
          address <- inetAddress
          result <- Managed
                     .make(AsynchronousServerSocketChannel())(_.close.orDie)
                     .use { server =>
                       for {
                         _ <- server.bind(address)
                         _ <- started.succeed(())
                         result <- Managed
                                    .make(server.accept)(_.close.orDie)
                                    .use(worker => worker.read(3) *> worker.read(3) *> ZIO.succeed(false))
                                    .catchAll {
                                      case ex: java.io.IOException if ex.getMessage == "Connection reset by peer" =>
                                        ZIO.succeed(true)
                                    }
                       } yield result
                     }
                     .fork
        } yield result

      def client: IO[Exception, Unit] =
        for {
          address <- inetAddress
          _ <- Managed.make(AsynchronousSocketChannel())(_.close.orDie).use { client =>
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
      } yield assert(same)(isTrue)
    },
    testM("close channel unbind port") {
      val inetAddress: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
        .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13376))

      def client: IO[Exception, Unit] =
        for {
          address <- inetAddress
          client  <- AsynchronousSocketChannel()
          _       <- client.connect(address)
          _       <- client.close
        } yield ()

      def server(started: Promise[Nothing, Unit]): IO[Exception, Fiber[Exception, Unit]] =
        for {
          address <- inetAddress
          server  <- AsynchronousServerSocketChannel()
          _       <- server.bind(address)
          _       <- started.succeed(())
          worker <- server.accept
                     .bracket(_.close.ignore *> server.close.ignore)(_ => ZIO.unit)
                     .fork
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
      } yield assertCompletes
    }
  )
}
