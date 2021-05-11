package zio.nio.channels

import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.BaseSpec
import zio.nio.core.Buffer
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }
import zio.{ Chunk, Has, IO, ZIO }

import java.io.{ File, RandomAccessFile }
import scala.io.Source

object ScatterGatherChannelSpec extends BaseSpec {

  override def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ] with Has[Blocking.Service], TestFailure[Any], TestSuccess] =
    suite("ScatterGatherChannelSpec")(
      testM("scattering read") {
        for {
          raf        <- ZIO.effectTotal(new RandomAccessFile("nio/src/test/resources/scattering_read_test.txt", "r"))
          fileChannel = raf.getChannel
          readLine    = (buffer: Buffer[Byte]) =>
                          for {
                            _     <- buffer.flip
                            array <- buffer.array
                            text   = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
                          } yield text
          buffs      <- IO.collectAll(List(Buffer.byte(5), Buffer.byte(5)))
          list       <- FileChannel(fileChannel).use { channel =>
                          for {
                            _    <- channel.read(buffs)
                            list <- ZIO.foreach(buffs)(readLine)
                          } yield list
                        }
        } yield assert(list)(equalTo("Hello" :: "World" :: Nil))
      },
      testM("gathering write") {
        for {
          file       <- ZIO.effect(new File("nio/src/test/resources/gathering_write_test.txt"))
          raf         = new RandomAccessFile(file, "rw")
          fileChannel = raf.getChannel

          buffs <- IO.collectAll(
                     List(
                       Buffer.byte(Chunk.fromArray("Hello".getBytes)),
                       Buffer.byte(Chunk.fromArray("World".getBytes))
                     )
                   )
          _     <- FileChannel(fileChannel).use(_.write(buffs).unit)
          result = Source.fromFile(file).getLines().toSeq
          _      = file.delete()
        } yield assert(result)(equalTo(Seq("HelloWorld")))
      }
    )
}
