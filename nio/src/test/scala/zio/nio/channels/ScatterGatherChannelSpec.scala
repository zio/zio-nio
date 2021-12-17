package zio.nio.channels

import zio.Clock
import zio.nio.file.Path
import zio.nio.{BaseSpec, Buffer}
import zio.Random
import zio.test.Assertion._
import zio.test._
import zio.test.{Live, TestClock, TestConsole, TestRandom, TestSystem}
import zio.{Chunk, IO, ZIO}

import java.nio.file.{Files, StandardOpenOption}
import scala.io.Source

object ScatterGatherChannelSpec extends BaseSpec {

  override def spec: Spec[
    Any with Annotations with Live with Sized with TestClock with TestConfig with TestConsole with TestRandom with TestSystem with Clock with zio.Console with zio.System with Random,
    TestFailure[Any],
    TestSuccess
  ] =
    suite("ScatterGatherChannelSpec")(
      test("scattering read") {
        FileChannel
          .open(Path("nio/src/test/resources/scattering_read_test.txt"), StandardOpenOption.READ)
          .useNioBlockingOps { ops =>
            for {
              buffs <- IO.collectAll(List(Buffer.byte(5), Buffer.byte(5)))
              _     <- ops.read(buffs)
              list <- ZIO.foreach(buffs) { (buffer: Buffer[Byte]) =>
                        for {
                          _     <- buffer.flip
                          array <- buffer.array
                          text   = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
                        } yield text

                      }
            } yield assert(list)(equalTo("Hello" :: "World" :: Nil))
          }
      },
      test("gathering write") {
        val file = Path("nio/src/test/resources/gathering_write_test.txt")
        FileChannel
          .open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
          .useNioBlockingOps { ops =>
            for {
              buffs <- IO.collectAll(
                         List(
                           Buffer.byte(Chunk.fromArray("Hello".getBytes)),
                           Buffer.byte(Chunk.fromArray("World".getBytes))
                         )
                       )
              _     <- ops.write(buffs)
              result = Source.fromFile(file.toFile).getLines().toSeq
            } yield assert(result)(equalTo(Seq("HelloWorld")))
          }
          .ensuring(IO.effectTotal(Files.delete(file.javaPath)))
      }
    )
}
