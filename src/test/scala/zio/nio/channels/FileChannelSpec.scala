package zio.nio.channels

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths, StandardOpenOption }

import zio.test._
import zio.test.Assertion._
import zio.nio.{ BaseSpec, Buffer }
import zio.{ Chunk, ZIO }

import scala.io.Source

object FileChannelSpec
    extends BaseSpec(
      suite("FileChannelSpec")(
        testM("asynchronous file buffer read") {
          val path = Paths.get("src/test/resources/async_file_read_test.txt")
          AsynchronousFileChannel
            .open(path, StandardOpenOption.READ)
            .use { channel =>
              for {
                buffer <- Buffer.byte(16)
                _      <- channel.readBuffer(buffer, 0)
                _      <- buffer.flip
                array  <- buffer.array
                text   = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
                _      <- channel.close
              } yield assert(text == "Hello World", isTrue)
            }
        },
        testM("asynchronous file chunk read") {
          val path = Paths.get("src/test/resources/async_file_read_test.txt")
          AsynchronousFileChannel
            .open(path, StandardOpenOption.READ)
            .use { channel =>
              for {
                bytes <- channel.read(500, 0L)
                _     <- channel.close
              } yield assert(bytes == Chunk.fromArray("Hello World".getBytes(StandardCharsets.UTF_8)), isTrue)
            }
        },
        testM("asynchronous file write") {
          val path = Paths.get("src/test/resources/async_file_write_test.txt")
          AsynchronousFileChannel
            .open(
              path,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE
            )
            .use {
              channel =>
                for {
                  buffer <- Buffer.byte(Chunk.fromArray("Hello World".getBytes))
                  _      <- channel.writeBuffer(buffer, 0)
                  _      <- channel.close
                  path   <- ZIO.effectTotal(Paths.get("src/test/resources/async_file_write_test.txt"))
                  result <- ZIO.effect(Source.fromFile(path.toFile()).getLines.toSeq)
                  _      <- ZIO.effect(Files.delete(path))
                } yield assert(result.size == 1 && result.head == "Hello World", isTrue)
            }
        },
        testM("memory mapped buffer") {
          val path = Paths.get("src/test/resources/async_file_read_test.txt")
          FileChannel
            .open(path, StandardOpenOption.READ)
            .bracket(_.close.ignore) { channel =>
              for {
                buffer <- channel.map(FileChannel.MapMode.READ_ONLY, 0L, 6L)
                bytes  <- buffer.getChunk()
              } yield assert(bytes == Chunk.fromArray("Hello ".getBytes(StandardCharsets.UTF_8)), isTrue)
            }
        }
      )
    )
