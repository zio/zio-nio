package zio.nio.channels

import zio.nio._
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
              _ <- DatagramChannel().use { server =>
                    for {
                      _          <- server.bind(address)
                      _          <- promise.succeed(())
                      retAddress <- server.receive(sink)
                      _          <- sink.flip
                      _          <- server.send(sink, retAddress)
                    } yield ()
                  }.fork
            } yield ()

          def echoClient: IO[Exception, Boolean] =
            for {
              address <- inetAddress
              src     <- Buffer.byte(3)
              result <- DatagramChannel().use { client =>
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
              _ <- DatagramChannel().use { client =>
                    client.connect(address).unit
                  }
            } yield ()

          def server(started: Promise[Nothing, Unit]): IO[Exception, Fiber[Exception, Unit]] =
            for {
              address <- inetAddress
              worker <- DatagramChannel().use { server =>
                         for {
                           _ <- server.bind(address)
                           _ <- started.succeed(())
                         } yield ()
                       }.fork
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
