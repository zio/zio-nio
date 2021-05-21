package zio.nio.channels

import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.BaseSpec
import zio.nio.core.Buffer
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }
import zio.{ Chunk, Has, ZIO }

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import scala.io.Source

object FileChannelSpec extends BaseSpec {

  override def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ] with Has[Blocking.Service], TestFailure[Any], TestSuccess] =
    suite("FileChannelSpec")(
      testM("asynchronous file buffer read") {
        val path = Path("nio/src/test/resources/async_file_read_test.txt")
        AsynchronousFileChannel
          .open(path, StandardOpenOption.READ)
          .use { channel =>
            for {
              buffer <- Buffer.byte(16)
              _      <- channel.read(buffer, 0)
              _      <- buffer.flip
              array  <- buffer.array
              text    = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
            } yield assert(text)(equalTo("Hello World"))
          }
      },
      testM("asynchronous file chunk read") {
        val path = Path("nio/src/test/resources/async_file_read_test.txt")
        AsynchronousFileChannel
          .open(path, StandardOpenOption.READ)
          .use { channel =>
            for {
              bytes <- channel.readChunk(500, 0L)
            } yield assert(bytes)(equalTo(Chunk.fromArray("Hello World".getBytes(StandardCharsets.UTF_8))))
          }
      },
      testM("asynchronous file write") {
        val path = Path("nio/src/test/resources/async_file_write_test.txt")
        AsynchronousFileChannel
          .open(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
          )
          .use { channel =>
            for {
              buffer <- Buffer.byte(Chunk.fromArray("Hello World".getBytes))
              _      <- channel.write(buffer, 0)
              path   <- ZIO.effectTotal(Path("nio/src/test/resources/async_file_write_test.txt"))
              result <- ZIO.effect(Source.fromFile(path.toFile).getLines().toSeq)
              _      <- Files.delete(path)
            } yield assert(result.size)(equalTo(1)) && assert(result.head)(equalTo("Hello World"))
          }
      },
      testM("memory mapped buffer") {
        val path = Path("nio/src/test/resources/async_file_read_test.txt")
        FileChannel
          .open(path, StandardOpenOption.READ)
          .use { channel =>
            for {
              buffer <- channel.map(FileChannel.MapMode.READ_ONLY, 0L, 6L)
              bytes  <- buffer.getChunk()
            } yield assert(bytes)(equalTo(Chunk.fromArray("Hello ".getBytes(StandardCharsets.UTF_8))))
          }
      }
    )
}
