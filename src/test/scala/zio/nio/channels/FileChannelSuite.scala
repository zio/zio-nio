package zio.nio.channels

import java.nio.file.{ Files, Paths, StandardOpenOption }

import testz.{ Harness, assert }
import zio.nio.Buffer
import zio.{ Chunk, DefaultRuntime }

import scala.io.Source

object FileChannelSuite extends DefaultRuntime {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("asynchronous file read") { () =>
        val path = Paths.get("src/test/resources/async_file_read_test.txt")

        val testProgram = for {
          channel <- AsynchronousFileChannel.open(path, StandardOpenOption.READ)
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
        val path = Paths.get("src/test/resources/async_file_write_test.txt")

        val testProgram = for {
          channel <- AsynchronousFileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
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
