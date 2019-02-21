package scalaz.nio

import java.nio.channels.{ 
  SocketChannel => JSocketChannel, 
  SelectionKey => JSelectionKey 
}

import scalaz.nio.channels.{ Selector, ServerSocketChannel, SocketChannel }
import scalaz.zio.duration._
import scalaz.zio.{ IO, RTS }
import testz.{ Harness, assert }

import scala.language.postfixOps

object SelectorSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(test("read/write") { () =>

      def server: IO[Exception, String] = {  
        for {
          address  <- SocketAddress.inetSocketAddress("0.0.0.0", 1111)
          selector <- Selector.make
          channel  <- ServerSocketChannel.open
          _        <- channel.bind(address)
          _        <- channel.configureBlocking(false)
          ops      <- channel.validOps
          key      <- channel.register(selector, ops, None)
    
          readyChannels <- selector.select
          selectedKeys  <- selector.selectedKeys
          key           =  selectedKeys.head
          clientOpt     <- channel.accept
          client        <- IO.fromEither(clientOpt.toRight(new RuntimeException("option empty")))
          _             <- client.configureBlocking(false)
          _             <- client.register(selector, JSelectionKey.OP_READ)
          addressOpt    <- client.localAddress
          _             <- selector.removeKey(key)
    
          readyChannels <- selector.select
          selectedKeys  <- selector.selectedKeys
          key           =  selectedKeys.head
          sClient       <- key.channel
          client        =  new SocketChannel(sClient.asInstanceOf[JSocketChannel])
          buffer        <- ByteBuffer(256)
          _             <- client.read(buffer)
          array         <- buffer.array
          text          =  array.takeWhile(_ != 10).map(_.toChar).mkString.trim
          _             <- client.close
          _             <- selector.removeKey(key)
    
          _ <- channel.close
        } yield text
      }

      def client: IO[Exception, Unit] =
        for {
          address <- SocketAddress.inetSocketAddress("0.0.0.0", 1111)
          client  <- SocketChannel.open(address)
          bytes   = "Hello".getBytes
          buffer  <- ByteBuffer(bytes)
          _       <- client.write(buffer)
          _       <- buffer.clear
          _       <- client.close
        } yield ()

      val testProgram: IO[Exception, Boolean] = for {
        serverFiber <- server.fork
        clientFiber <- client.delay(10 millis).fork
        message     <- serverFiber.join
         _           <- clientFiber.join
      } yield message == "Hello"

      assert(unsafeRun(testProgram))
    })
  }
}
