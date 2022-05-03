package zio.nio.channels

import zio._
import zio.nio.channels.SelectionKey.Operation
import zio.nio.{BaseSpec, Buffer, ByteBuffer, SocketAddress}
import zio.test.Assertion._
import zio.test._

import java.io.IOException
import java.nio.channels.CancelledKeyException

object SelectorSpec extends BaseSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SelectorSpec")(
      test("read/write") {
        for {
          started     <- Promise.make[Nothing, SocketAddress]
          serverFiber <- ZIO.scoped(server(started)).fork
          addr        <- started.await
          clientFiber <- client(addr).fork
          _           <- serverFiber.join
          message     <- clientFiber.join
        } yield assert(message)(equalTo("Hello world"))
      },
      test("select is interruptible") {
        live {
          ZIO.scoped {
            Selector.open.flatMap { selector =>
              for {
                fiber <- selector.select.fork
                _     <- ZIO.sleep(500.milliseconds)
                exit  <- fiber.interrupt
              } yield assert(exit)(isInterrupted)
            }
          }
        }
      }
    )

  def byteArrayToString(array: Array[Byte]): String = array.takeWhile(_ != 10).map(_.toChar).mkString.trim

  def safeStatusCheck(statusCheck: IO[CancelledKeyException, Boolean])(implicit
    trace: Trace
  ): IO[Nothing, Boolean] =
    statusCheck.fold(_ => false, identity)

  def server(
    started: Promise[Nothing, SocketAddress]
  )(implicit trace: Trace): ZIO[Scope, Exception, Unit] = {
    def serverLoop(
      scope: Scope,
      selector: Selector,
      buffer: ByteBuffer
    )(implicit trace: Trace): ZIO[Any, Exception, Unit] =
      for {
        _ <- selector.select
        _ <- selector.foreachSelectedKey { key =>
               key.matchChannel { readyOps =>
                 {
                   case channel: ServerSocketChannel if readyOps(Operation.Accept) =>
                     for {
                       scopeResult <- scope.extend(channel.flatMapNonBlocking(_.accept))
                       maybeClient  = scopeResult
                       _ <- ZIO.whenCase(maybeClient) { case Some(client) =>
                              client.configureBlocking(false) *> client.register(selector, Set(Operation.Read))
                            }
                     } yield ()
                   case channel: SocketChannel if readyOps(Operation.Read) =>
                     channel.flatMapNonBlocking { client =>
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
      scope    <- ZIO.scope
      selector <- Selector.open
      channel  <- ServerSocketChannel.open
      _ <- for {
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
    } yield ()
  }

  def client(address: SocketAddress)(implicit trace: Trace): IO[IOException, String] = {
    val bytes = Chunk.fromArray("Hello world".getBytes)
    for {
      buffer <- Buffer.byte(bytes)
      text <- ZIO.scoped {
                SocketChannel.open(address).flatMapNioBlockingOps { client =>
                  for {
                    _     <- client.write(buffer)
                    _     <- buffer.clear
                    _     <- client.read(buffer)
                    array <- buffer.array
                    text   = byteArrayToString(array)
                    _     <- buffer.clear
                  } yield text
                }
              }
    } yield text
  }
}
