package scalaz.nio

import java.nio.channels.{ 
  SocketChannel => JSocketChannel, 
  SelectionKey => JSelectionKey 
}

import scalaz.nio.channels.{ Selector, ServerSocketChannel, SocketChannel }
import scalaz.zio.duration._
import scalaz.zio.{ IO, RTS, Schedule }
import testz.{ Harness, assert }

import scala.language.postfixOps

object SelectorSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(test("read/write") { () =>

      def byteArrayToString(array: Array[Byte]): String =
        array.takeWhile(_ != 10).map(_.toChar).mkString.trim

      val addressIo = SocketAddress.inetSocketAddress("0.0.0.0", 1111)

      def serverLoop(selector: Selector, channel: ServerSocketChannel, buffer: ByteBuffer): IO[Exception, Unit] =
        for {
          readyChannels <- selector.select(10 millis)
          selectedKeys  <- selector.selectedKeys
          _             <- IO.foreach(selectedKeys) { key =>
            IO.when(key.isAcceptable) {
              for {
                clientOpt <- channel.accept
                client    <- IO.fromEither(clientOpt.toRight(new RuntimeException("option empty")))
                _         <- client.configureBlocking(false)
                _         <- client.register(selector, JSelectionKey.OP_READ)
              } yield ()
            } *>
            IO.when(key.isReadable) {
              for {
                sClient <- key.channel
                client  =  new SocketChannel(sClient.asInstanceOf[JSocketChannel])
                _       <- client.read(buffer)
                array   <- buffer.array
                text    =  array.takeWhile(_ != 10).map(_.toChar).mkString.trim
                _       <- buffer.flip
                _       <- client.write(buffer)
                _       <- buffer.clear
                _       <- client.close      
              } yield ()
            } *>
            selector.removeKey(key)
          }
        } yield ()

      def server: IO[Exception, Unit] = {  
        for {
          address  <- addressIo
          selector <- Selector.make
          channel  <- ServerSocketChannel.open
          _        <- channel.bind(address)
          _        <- channel.configureBlocking(false)
          ops      <- channel.validOps
          key      <- channel.register(selector, ops, None)
          buffer   <- ByteBuffer(256)
    
          _ <- serverLoop(selector, channel, buffer).repeat(Schedule.recurs(2))
    
          _ <- channel.close
        } yield ()
      }

      def client: IO[Exception, String] =
        for {
          address <- addressIo
          client  <- SocketChannel.open(address)
          bytes   = "Hello".getBytes
          buffer  <- ByteBuffer(bytes)
          _       <- client.write(buffer)
          _       <- buffer.clear
          _       <- client.read(buffer)
          array   <- buffer.array
          text    = byteArrayToString(array) 
          _       <- buffer.clear
        } yield text

      val testProgram: IO[Exception, Boolean] = for {
        serverFiber <- server.fork
        clientFiber <- client.delay(10 millis).fork
        _           <- serverFiber.join
        message     <- clientFiber.join
      } yield message == "Hello"

      assert(unsafeRun(testProgram))
    })
  }
}
