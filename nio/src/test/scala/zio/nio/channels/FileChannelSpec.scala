package zio.nio.channels

import zio.nio.charset.Charset
import zio.nio.file.{Files, Path}
import zio.nio.{BaseSpec, Buffer}
import zio.stream.Stream
import zio.test.Assertion._
import zio.test._
import zio.{Chunk, Clock, Random, UIO, ZIO}

import java.io.EOFException
import java.nio.file.StandardOpenOption
import scala.io.Source

object FileChannelSpec extends BaseSpec {

  private val readFile = Path("nio/src/test/resources/async_file_read_test.txt")

  private val readFileContents = "Hello World"

  def loadViaSource(path: Path): UIO[List[String]] =
    ZIO
      .attempt(Source.fromFile(path.toFile))
      .acquireReleaseWith(s => ZIO.succeed(s.close()))(s => ZIO.attemptBlocking(s.getLines().toList))
      .orDie

  override def spec: Spec[
    Annotations with Live with Sized with TestClock with TestConfig with TestConsole with TestRandom with TestSystem with Clock with zio.Console with zio.System with Random,
    TestFailure[Any],
    TestSuccess
  ] =
    suite("FileChannelSpec")(
      test("asynchronous file buffer read") {
        AsynchronousFileChannel.open(readFile, StandardOpenOption.READ).use { channel =>
          for {
            buffer <- Buffer.byte(16)
            _      <- channel.read(buffer, 0)
            _      <- buffer.flip
            array  <- buffer.array
            text    = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
          } yield assert(text)(equalTo(readFileContents))
        }
      },
      test("asynchronous file chunk read") {
        AsynchronousFileChannel.open(readFile, StandardOpenOption.READ).use { channel =>
          for {
            bytes <- channel.readChunk(500, 0L)
            chars <- Charset.Standard.utf8.decodeChunk(bytes)
          } yield assert(chars.mkString)(equalTo(readFileContents))
        }
      },
      test("asynchronous file write") {
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
              result <- loadViaSource(path)
              _      <- Files.delete(path)
            } yield assert(result.size)(equalTo(1)) && assert(result.head)(equalTo(readFileContents))

          }
      },
      test("memory mapped buffer") {
        for {
          result <- FileChannel
                      .open(readFile, StandardOpenOption.READ)
                      .useNioBlockingOps { ops =>
                        for {
                          buffer <- ops.map(FileChannel.MapMode.READ_ONLY, 0L, 6L)
                          bytes  <- buffer.getChunk()
                          chars  <- Charset.Standard.utf8.decodeChunk(bytes)
                        } yield assert(chars.mkString)(equalTo(readFileContents.take(6)))
                      }
        } yield result
      },
      test("end of stream") {
        FileChannel
          .open(readFile, StandardOpenOption.READ)
          .useNioBlocking { (channel, ops) =>
            for {
              size <- channel.size
              _    <- ops.readChunk(size.toInt)
              _    <- ops.readChunk(1)
            } yield ()
          }
          .flip
          .map(assert(_)(isSubtype[EOFException](anything)))
      },
      test("stream reading") {
        FileChannel
          .open(readFile, StandardOpenOption.READ)
          .useNioBlockingOps {
            _.stream().via(Charset.Standard.utf8.newDecoder.transducer()).runCollect.map(_.mkString)
          }
          .map(assert(_)(equalTo(readFileContents)))
      },
      test("sink writing") {
        val testData =
          """Yet such is oft the course of deeds that move the wheels of the world:
            | small hands do them because they must, while the eyes of the great are elsewhere.""".stripMargin
        val stream = Stream.fromIterable(testData).via(Charset.Standard.utf8.newEncoder.transducer())
        val file   = Path("nio/src/test/resources/sink_write_test.txt")
        FileChannel
          .open(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
          .useNioBlockingOps(channel => stream.run(channel.sink()))
          .zipRight(loadViaSource(file).ensuring(Files.delete(file).orDie))
          .map(lines => assert(lines.mkString("\n"))(equalTo(testData)))
      }
    )
}
