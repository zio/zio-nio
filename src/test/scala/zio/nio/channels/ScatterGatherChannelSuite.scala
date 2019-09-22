package zio.nio.channels

import java.io.{ File, RandomAccessFile }

import testz.{ Harness, assert }
import zio.nio.Buffer
import zio.{ Chunk, DefaultRuntime, IO }

import scala.io.Source

object ScatterGatherChannelSuite extends DefaultRuntime {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("scattering read") { () =>
        val raf         = new RandomAccessFile("src/test/resources/scattering_read_test.txt", "r")
        val fileChannel = raf.getChannel()

        val readLine: Buffer[Byte] => IO[Exception, String] = buffer =>
          for {
            _     <- buffer.flip
            array <- buffer.array
            text  = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
          } yield text

        val testProgram = for {
          buffs <- IO.collectAll(Seq(Buffer.byte(5), Buffer.byte(5)))
          list <- FileChannel(fileChannel).use { channel =>
                   for {
                     _    <- channel.readBuffer(buffs)
                     list <- IO.collectAll(buffs.map(readLine))
                   } yield list
                 }
        } yield list

        val t1 :: t2 :: Nil = unsafeRun(testProgram)

        assert(t1 == "Hello")
        assert(t2 == "World")
      },
      test("gathering write") { () =>
        val file        = new File("src/test/resources/gathering_write_test.txt")
        val raf         = new RandomAccessFile(file, "rw")
        val fileChannel = raf.getChannel()

        val testProgram = for {
          buffs <- IO.collectAll(
                    Seq(
                      Buffer.byte(Chunk.fromArray("Hello".getBytes)),
                      Buffer.byte(Chunk.fromArray("World".getBytes))
                    )
                  )
          _ <- FileChannel(fileChannel).use { channel =>
                for {
                  _ <- channel.writeBuffer(buffs)
                } yield ()

              }
        } yield ()

        unsafeRun(testProgram)

        val result = Source.fromFile(file).getLines.toSeq
        file.delete()

        assert(result.size == 1)
        assert(result.head == "HelloWorld")
      }
    )
  }

}
