package zio.nio.core.channels

import zio.nio.core._
import zio.test.Assertion._
import zio.test.{ suite, testM, _ }
import zio.{ IO, _ }

object DatagramChannelSpec
    extends BaseSpec(
      suite("DatagramChannelSpec")(
        testM("read/write") {
          val inetAddress: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
            .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13371))

          def echoServer(promise: Promise[Nothing, Unit]): IO[Exception, Unit] =
            for {
              address <- inetAddress
              sink    <- Buffer.byte(3)
              _ <- Managed
                    .make(DatagramChannel.open)(_.close.orDie)
                    .use { server =>
                      for {
                        _          <- server.bind(Some(address))
                        _          <- promise.succeed(())
                        retAddress <- server.receive(sink)
                        addr       <- IO.fromOption(retAddress)
                        _          <- sink.flip
                        _          <- server.send(sink, addr)
                      } yield ()
                    }
                    .fork
            } yield ()

          def echoClient: IO[Exception, Boolean] =
            for {
              address <- inetAddress
              src     <- Buffer.byte(3)
              result <- Managed.make(DatagramChannel.open)(_.close.orDie).use { client =>
                         for {
                           _        <- client.connect(address)
                           sent     <- src.array
                           _        = sent.update(0, 1)
                           _        <- client.send(src, address)
                           _        <- src.flip
                           _        <- client.read(src)
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
        testM("close channel unbind port") {
          val inetAddress: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
            .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13373))

          def client: IO[Exception, Unit] =
            for {
              address <- inetAddress
              _       <- Managed.make(DatagramChannel.open)(_.close.orDie).use(client => client.connect(address).unit)
            } yield ()

          def server(started: Promise[Nothing, Unit]): IO[Exception, Fiber[Exception, Unit]] =
            for {
              address <- inetAddress
              worker <- Managed
                         .make(DatagramChannel.open)(_.close.orDie)
                         .use { server =>
                           for {
                             _ <- server.bind(Some(address))
                             _ <- started.succeed(())
                           } yield ()
                         }
                         .fork
            } yield worker

          for {
            serverStarted  <- Promise.make[Nothing, Unit]
            s1             <- server(serverStarted)
            _              <- serverStarted.await
            _              <- client
            _              <- s1.join
            serverStarted2 <- Promise.make[Nothing, Unit]
            s2             <- server(serverStarted2)
            _              <- serverStarted2.await
            _              <- client
            _              <- s2.join
          } yield assert(true, isTrue)
        }
      )
    )
