package zio.nio.channels

import zio.nio.file.Path
import zio.nio.{BaseSpec, Buffer}
import zio.test.Assertion._
import zio.test._
import zio.{Chunk, IO, Scope, ZIO}

import java.io.IOException
import java.nio.file.{Files, StandardOpenOption}
import scala.io.Source

object ScatterGatherChannelSpec extends BaseSpec {

  override def spec: Spec[Scope with Any, IOException] =
    suite("ScatterGatherChannelSpec")(
      test("scattering read") {
        FileChannel
          .open(Path("nio/src/test/resources/scattering_read_test.txt"), StandardOpenOption.READ)
          .flatMapNioBlockingOps { ops =>
            for {
              buffs <- ZIO.collectAll(List(Buffer.byte(5), Buffer.byte(5)))
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
          .flatMapNioBlockingOps { ops =>
            for {
              buffs <- ZIO.collectAll(
                         List(
                           Buffer.byte(Chunk.fromArray("Hello".getBytes)),
                           Buffer.byte(Chunk.fromArray("World".getBytes))
                         )
                       )
              _     <- ops.write(buffs)
              result = Source.fromFile(file.toFile).getLines().toSeq
            } yield assert(result)(equalTo(Seq("HelloWorld")))
          }
          .ensuring(ZIO.succeed(Files.delete(file.javaPath)))
      }
    )
}
