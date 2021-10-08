package zio.nio.channels

import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.file.Path
import zio.nio.{BaseSpec, Buffer}
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{Live, TestClock, TestConsole, TestRandom, TestSystem}
import zio.{Chunk, Has, IO, ZIO}

import java.nio.file.{Files, StandardOpenOption}
import scala.io.Source

object ScatterGatherChannelSpec extends BaseSpec {

  override def spec: Spec[Any with Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ] with Has[Blocking.Service] with Has[Blocking.Service], TestFailure[Any], TestSuccess] =
    suite("ScatterGatherChannelSpec")(
      testM("scattering read") {
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
      testM("gathering write") {
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
