package zio.nio.channels

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths, StandardOpenOption }

import testz.{ Harness, assert }
import zio.nio.Buffer
import zio.{ Chunk, DefaultRuntime }

import scala.io.Source

object FileChannelSuite extends DefaultRuntime {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("asynchronous file buffer read") { () =>
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
      test("asynchronous file chunk read") { () =>
        val path = Paths.get("src/test/resources/async_file_read_test.txt")

        val testProgram = for {
          channel <- AsynchronousFileChannel.open(path, StandardOpenOption.READ)
          bytes   <- channel.read(500, 0L)
          _       <- channel.close
        } yield bytes

        val result = unsafeRun(testProgram)

        assert(result == Chunk.fromArray("Hello World".getBytes(StandardCharsets.UTF_8)))
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
      },
      test("memory mapped buffer") { () =>
        val path = Paths.get("src/test/resources/async_file_read_test.txt")

        val testProgram = {
          FileChannel.open(path, StandardOpenOption.READ).bracket(_.close.ignore) { channel =>
            for {
              buffer <- channel.map(FileChannel.MapMode.READ_ONLY, 0L, 6L)
              bytes  <- buffer.getChunk()
            } yield bytes
          }
        }

        val result = unsafeRun(testProgram)

        assert(result == Chunk.fromArray("Hello ".getBytes(StandardCharsets.UTF_8)))
      }
    )
  }

}
