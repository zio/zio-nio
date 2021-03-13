package zio.nio.core.channels

import java.nio.file.{ Files, StandardOpenOption }

import zio.nio.core.file.Path
import zio.nio.core.{ BaseSpec, Buffer }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, IO, ZIO }

import scala.io.Source

object ScatterGatherChannelSpec extends BaseSpec {

  override def spec =
    suite("ScatterGatherChannelSpec")(
      testM("scattering read") {
        FileChannel
          .open(Path("nio-core/src/test/resources/scattering_read_test.txt"), StandardOpenOption.READ)
          .useNioBlockingOps { ops =>
            for {
              buffs <- IO.collectAll(List(Buffer.byte(5), Buffer.byte(5)))
              _     <- ops.read(buffs)
              list  <- ZIO.foreach(buffs) { (buffer: Buffer[Byte]) =>
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
        val file = Path("nio-core/src/test/resources/gathering_write_test.txt")
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
