package zio.nio.channels

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.nio.channels.SelectionKey.Operation
import zio.nio.{BaseSpec, Buffer, ByteBuffer, SocketAddress}
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{Live, TestClock, TestConsole, TestRandom, TestSystem, live}

import java.io.IOException
import java.nio.channels.CancelledKeyException

object SelectorSpec extends BaseSpec {

  override def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ] with Has[Blocking.Service], TestFailure[Any], TestSuccess] =
    suite("SelectorSpec")(
      testM("read/write") {
        for {
          started     <- Promise.make[Nothing, SocketAddress]
          serverFiber <- server(started).useNow.fork
          addr        <- started.await
          clientFiber <- client(addr).fork
          _           <- serverFiber.join
          message     <- clientFiber.join
        } yield assert(message)(equalTo("Hello world"))
      },
      testM("select is interruptible") {
        live {
          Selector.open.use { selector =>
            for {
              fiber <- selector.select.fork
              _     <- ZIO.sleep(500.milliseconds)
              exit  <- fiber.interrupt
            } yield assert(exit)(isInterrupted)
          }
        }
      }
    )

  def byteArrayToString(array: Array[Byte]): String = array.takeWhile(_ != 10).map(_.toChar).mkString.trim

  def safeStatusCheck(statusCheck: IO[CancelledKeyException, Boolean]): IO[Nothing, Boolean] =
    statusCheck.fold(_ => false, identity)

  def server(started: Promise[Nothing, SocketAddress]): ZManaged[Clock with Blocking, Exception, Unit] = {
    def serverLoop(
      scope: Managed.Scope,
      selector: Selector,
      buffer: ByteBuffer
    ): ZIO[Blocking, Exception, Unit] =
      for {
        _ <- selector.select
        _ <- selector.foreachSelectedKey { key =>
               key.matchChannel { readyOps =>
                 {
                   case channel: ServerSocketChannel if readyOps(Operation.Accept) =>
                     for {
                       scopeResult     <- scope(channel.useNonBlockingManaged(_.accept))
                       (_, maybeClient) = scopeResult
                       _ <- IO.whenCase(maybeClient) { case Some(client) =>
                              client.configureBlocking(false) *> client.register(selector, Set(Operation.Read))
                            }
                     } yield ()
                   case channel: SocketChannel if readyOps(Operation.Read) =>
                     channel.useNonBlocking { client =>
                       for {
                         _ <- client.read(buffer)
                         _ <- buffer.flip
                         _ <- client.write(buffer)
                         _ <- buffer.clear
                         _ <- channel.close
                       } yield ()
                     }
                 }
               }
                 .as(true)
             }
        _ <- selector.selectedKeys.filterOrDieMessage(_.isEmpty)("Selected key set should be empty")
      } yield ()

    for {
      scope    <- Managed.scope
      selector <- Selector.open
      channel  <- ServerSocketChannel.open
      _ <- Managed.fromEffect {
             for {
               _      <- channel.bindAuto()
               _      <- channel.configureBlocking(false)
               _      <- channel.register(selector, Set(Operation.Accept))
               buffer <- Buffer.byte(256)
               addr   <- channel.localAddress
               _      <- started.succeed(addr)

               /*
                *  we need to run the server loop twice:
                *  1. to accept the client request
                *  2. to read from the client channel
                */
               _ <- serverLoop(scope, selector, buffer).repeat(Schedule.once)
             } yield ()
           }
    } yield ()
  }

  def client(address: SocketAddress): ZIO[Blocking, IOException, String] = {
    val bytes = Chunk.fromArray("Hello world".getBytes)
    for {
      buffer <- Buffer.byte(bytes)
      text <- SocketChannel.open(address).useNioBlockingOps { client =>
                for {
                  _     <- client.write(buffer)
                  _     <- buffer.clear
                  _     <- client.read(buffer)
                  array <- buffer.array
                  text   = byteArrayToString(array)
                  _     <- buffer.clear
                } yield text
              }
    } yield text
  }
}
