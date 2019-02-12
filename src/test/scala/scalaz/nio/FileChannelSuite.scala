package scalaz.nio

import java.nio.file.{ Files, Paths, StandardOpenOption }

import scalaz.nio.channels.AsynchronousFileChannel
import scalaz.zio.{ Chunk, RTS }
import testz.{ Harness, assert }

import scala.io.Source

object FileChannelSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("asynchronous file read") { () =>
        val path = Paths.get("src/test/resources/async_file_read_test.txt")

        val testProgram = for {
          channel <- AsynchronousFileChannel.open(path, Set(StandardOpenOption.READ))
          buffer  <- Buffer.byte(16)
          _       <- channel.readBuffer(buffer, 0)
          _       <- buffer.flip
          array   <- buffer.array
          text    = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
          _       <- channel.close
        } yield text

        val result = unsafeRun(testProgram)

        assert(result == "Hello World")
      },
      test("asynchronous file write") { () =>
        val path    = Paths.get("src/test/resources/async_file_write_test.txt")
        val options = Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE)

        val testProgram = for {
          channel <- AsynchronousFileChannel.open(path, options)
          buffer  <- Buffer.byte(Chunk.fromArray("Hello World".getBytes))
          _       <- channel.writeBuffer(buffer, 0)
          _       <- channel.close
        } yield ()

        unsafeRun(testProgram)

        val result = Source.fromFile(path.toFile()).getLines.toSeq
        Files.delete(path)

        assert(result.size == 1)
        assert(result.head == "Hello World")
      }
    )
  }

}
