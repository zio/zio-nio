package zio
package nio
package examples


import zio.nio.channels.{FileChannel, ManagedBlockingNioOps}
import zio.nio.charset.Charset
import zio.nio.file.Path
import zio.stream.ZStream
import zio.{ Console, Console, ZIOAppDefault }

/**
 * Dumps a text file to the console using a specified encoding.
 *
 * Two command line parameters must be provided:
 *   1. The path of the file to dump 2. The character encoding to use — optional, defaults to UTF-8
 */
object TextFileDump extends ZIOAppDefault {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val charset = (args match {
      case _ :: s :: _ => Some(s)
      case _           => None
    }).flatMap(Charset.forNameIfSupported).getOrElse(Charset.Standard.utf8)

    val program = for {
      fileArg <- ZIO.succeed(args.headOption).someOrFail(new Exception("File name must be specified"))
      _       <- dump(charset, Path(fileArg))
    } yield ()

    program.exitCode
  }

  private def dump(charset: Charset, file: Path): ZIO[Console with Any, Exception, Unit] =
    FileChannel.open(file).useNioBlockingOps { fileOps =>
      val inStream: ZStream[Any, Exception, Byte] = ZStream.repeatZIOChunkOption {
        fileOps.readChunk(1000).asSomeError.flatMap { chunk =>
          if (chunk.isEmpty) ZIO.fail(None) else ZIO.succeed(chunk)
        }
      }

      // apply decoding transducer
      val charStream: ZStream[Any, Exception, Char] =
        inStream.transduce(charset.newDecoder.transducer())

      charStream.runForeachChunk(chars => Console.print(chars.mkString))
    }

}
