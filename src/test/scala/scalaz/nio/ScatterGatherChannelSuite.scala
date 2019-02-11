package scalaz.nio

import java.io.{ File, RandomAccessFile }

import scalaz.nio.channels.{ GatheringByteChannel, ScatteringByteChannel }
import scalaz.zio.{ IO, RTS }
import testz.{ Harness, assert }

import scala.io.Source

object ScatterGatherChannelSuite extends RTS {

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("scattering read") { () =>
        val raf         = new RandomAccessFile("src/test/resources/scatter_hello_world.txt", "r")
        val fileChannel = raf.getChannel()

        val readLine: ByteBuffer => IO[Exception, String] = buffer =>
          for {
            _     <- buffer.flip
            array <- buffer.array
            text  = array.takeWhile(_ != 10).map(_.toChar).mkString.trim
          } yield text

        val testProgram = for {
          buffs   <- IO.collectAll(Seq(ByteBuffer(5), ByteBuffer(5)))
          channel = new ScatteringByteChannel(fileChannel)
          _       <- channel.read(buffs)
          list    <- IO.collectAll(buffs.map(readLine))
        } yield list

        val t1 :: t2 :: Nil = unsafeRun(testProgram)

        assert(t1 == "Hello")
        assert(t2 == "World")
      },
      test("gathering write") { () =>
        val file        = new File("src/test/resources/gather_hello_world.txt")
        val raf         = new RandomAccessFile(file, "rw")
        val fileChannel = raf.getChannel()

        val testProgram = for {
          buffs   <- IO.collectAll(Seq(ByteBuffer("Hello".getBytes), ByteBuffer("World".getBytes)))
          channel = new GatheringByteChannel(fileChannel)
          _       <- channel.write(buffs)
          _       <- channel.close
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
