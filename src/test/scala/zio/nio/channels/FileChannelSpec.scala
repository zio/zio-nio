package zio.nio.channels

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption

import zio.nio.file.{ Files, Path }
import zio.test._
import zio.test.Assertion._
import zio.nio.{ BaseSpec, Buffer }
import zio.{ Chunk, ZIO }

import scala.io.Source

object FileChannelSpec
    extends BaseSpec(
      suite("FileChannelSpec")(
        testM("asynchronous file buffer read") {
          val path = Path("src/test/resources/async_file_read_test.txt")
          AsynchronousFileChannel
            .open(path, StandardOpenOption.READ)
            .use { channel =>
              for {
                buffer <- Buffer.byte(16)
                _      <- channel.readBuffer(buffer, 0)
                _      <- buffer.flip
                array  <- buffer.array
                text   = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
              } yield assert(text == "Hello World", isTrue)
            }
        },
        testM("asynchronous file chunk read") {
          val path = Path("src/test/resources/async_file_read_test.txt")
          AsynchronousFileChannel
            .open(path, StandardOpenOption.READ)
            .use { channel =>
              for {
                bytes <- channel.read(500, 0L)
              } yield assert(bytes == Chunk.fromArray("Hello World".getBytes(StandardCharsets.UTF_8)), isTrue)
            }
        },
        testM("asynchronous file write") {
          val path = Path("src/test/resources/async_file_write_test.txt")
          AsynchronousFileChannel
            .open(
              path,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE
            )
            .use { channel =>
              for {
                buffer <- Buffer.byte(Chunk.fromArray("Hello World".getBytes))
                _      <- channel.writeBuffer(buffer, 0)
                path   <- ZIO.effectTotal(Path("src/test/resources/async_file_write_test.txt"))
                result <- ZIO.effect(Source.fromFile(path.toFile).getLines.toSeq)
                _      <- Files.delete(path)
              } yield assert(result.size == 1 && result.head == "Hello World", isTrue)
            }
        },
        testM("memory mapped buffer") {
          val path = Path("src/test/resources/async_file_read_test.txt")
          FileChannel
            .open(path, StandardOpenOption.READ)
            .use { channel =>
              for {
                buffer <- channel.map(FileChannel.MapMode.READ_ONLY, 0L, 6L)
                bytes  <- buffer.getChunk()
              } yield assert(bytes == Chunk.fromArray("Hello ".getBytes(StandardCharsets.UTF_8)), isTrue)
            }
        }
      )
    )
