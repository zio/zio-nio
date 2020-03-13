package zio.nio.core.channels

import java.io.{ File, RandomAccessFile }

import zio.nio.core.{ BaseSpec, Buffer }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, IO, ZIO }

import scala.io.Source

object ScatterGatherChannelSpec extends BaseSpec {

  override def spec = suite("ScatterGatherChannelSpec")(
    testM("scattering read") {
      for {
        raf         <- ZIO.effectTotal(new RandomAccessFile("nio-core/src/test/resources/scattering_read_test.txt", "r"))
        fileChannel = raf.getChannel
        readLine = (buffer: Buffer[Byte]) =>
          for {
            _     <- buffer.flip
            array <- buffer.array
            text  = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
          } yield text
        buffs   <- IO.collectAll(Seq(Buffer.byte(5), Buffer.byte(5)))
        channel = new FileChannel(fileChannel)
        _       <- channel.readBuffer(buffs)
        list    <- IO.collectAll(buffs.map(readLine))
        _       <- channel.close
      } yield assert(list)(equalTo("Hello" :: "World" :: Nil))
    },
    testM("gathering write") {
      for {
        file        <- ZIO.effect(new File("nio-core/src/test/resources/gathering_write_test.txt"))
        raf         = new RandomAccessFile(file, "rw")
        fileChannel = raf.getChannel

        buffs <- IO.collectAll(
                  Seq(
                    Buffer.byte(Chunk.fromArray("Hello".getBytes)),
                    Buffer.byte(Chunk.fromArray("World".getBytes))
                  )
                )
        channel = new FileChannel(fileChannel)
        _       <- channel.writeBuffer(buffs)
        _       <- channel.close
        result  = Source.fromFile(file).getLines.toSeq
        _       = file.delete()
      } yield assert(result)(equalTo(Seq("HelloWorld")))
    }
  )
}
