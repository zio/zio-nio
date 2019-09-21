package zio.nio.channels

import zio.duration._
import zio.nio.{ Buffer, InetAddress, InetSocketAddress, SocketAddress }
import zio.test.mock._
import zio.test.{ DefaultRunnableSpec, TestAspect, ZSpec, suite, testM }
import zio.{ IO, _ }
import zio.test._
import zio.test.Assertion._
import ChannelSpecUtil._

abstract class ZIOBaseSpec(spec: => ZSpec[MockEnvironment, Throwable, String, Any])
    extends DefaultRunnableSpec(spec, List(TestAspect.timeout(60.seconds)))

object ChannelSpecUtil {

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

  val inetAddress2: ZIO[Any, Exception, InetSocketAddress] = InetAddress.localHost
    .flatMap(iAddr => SocketAddress.inetSocketAddress(iAddr, 13376))

  def client2: IO[Exception, Unit] =
    for {
      address <- inetAddress2
      _ <- AsynchronousSocketChannel().use { client =>
            client.connect(address).unit
          }
    } yield ()

  def server2(started: Promise[Nothing, Unit]): IO[Exception, Fiber[Exception, Unit]] =
    for {
      address <- inetAddress2
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
}

object ChannelSpec
    extends ZIOBaseSpec(
      suite("ChannelSpec")(
        testM[MockEnvironment, Exception, String]("read/write") {
          for {
            serverStarted <- Promise.make[Nothing, Unit]
            _             <- echoServer(serverStarted)
            _             <- serverStarted.await
            same          <- echoClient
          } yield assert(same, isTrue)
        },
        testM[MockEnvironment, Exception, String]("read should fail when connection close") {
          for {
            serverStarted <- Promise.make[Nothing, Unit]
            serverFiber   <- server(serverStarted)
            _             <- serverStarted.await
            _             <- client
            same          <- serverFiber.join
          } yield assert(same, isTrue)
        },
        testM[MockEnvironment, Exception, String]("close channel unbind port") {
          for {
            serverStarted  <- Promise.make[Nothing, Unit]
            s1             <- server2(serverStarted)
            _              <- serverStarted.await
            _              <- client2
            _              <- s1.join
            serverStarted2 <- Promise.make[Nothing, Unit]
            s2             <- server(serverStarted2)
            _              <- serverStarted2.await
            _              <- client2
            _              <- s2.join
          } yield assert(true, isTrue)
        }
      )
    )
