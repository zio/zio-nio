package scalaz.nio

import java.net.InetSocketAddress
import scalaz.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import scalaz.zio.IO
import scalaz.zio.RTS
import testz.{ Harness, assert }

object ChannelSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("read/write") { () =>
        val testProgram: IO[Exception, Boolean] = for {
          src     <- Buffer.byte(3)
          sink    <- Buffer.byte(3)
          address <- IO.now(new InetSocketAddress(1337))
          server  <- AsynchronousServerSocketChannel()
          _       <- server.bind(address)
          client  <- AsynchronousSocketChannel()
          _       <- client.connect(address)
          // in this senario accept should be called after client is connected
          worker  <- server.accept
          nSrc    <- client.write(src)
          nSink  <- worker.read(sink)
        } yield nSrc == nSink

        assert(unsafeRun(testProgram))
      }
    )
  }
}
