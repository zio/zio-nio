package zio
package nio
package examples

import zio.nio.channels.{BlockingNioOps, FileChannel}
import zio.nio.charset.Charset
import zio.nio.file.Path
import zio.stream.ZStream

/**
 * Dumps a text file to the console using a specified encoding.
 *
 * Two command line parameters must be provided:
 *   1. The path of the file to dump 2. The character encoding to use — optional, defaults to UTF-8
 */
object TextFileDump extends ZIOAppDefault {

  override def run: URIO[ZIOAppArgs, ExitCode] = {
    val charsetEff = ZIO.serviceWith[ZIOAppArgs](s =>
      (s.getArgs.toList match {
        case _ :: s :: _ => Some(s)
        case _           => None
      }).flatMap(Charset.forNameIfSupported).getOrElse(Charset.Standard.utf8)
    )

    val program = for {
      fileArg <-
        ZIO.serviceWith[ZIOAppArgs](_.getArgs.toSeq.headOption).someOrFail(new Exception("File name must be specified"))
      charset <- charsetEff
      _       <- dump(charset, Path(fileArg))
    } yield ()

    program.exitCode
  }

  private def dump(charset: Charset, file: Path)(implicit
    trace: Trace
  ): ZIO[Any, Exception, Unit] =
    ZIO.scoped {
      FileChannel.open(file).flatMapNioBlockingOps { fileOps =>
        val inStream: ZStream[Any, Exception, Byte] = ZStream.repeatZIOChunkOption {
          fileOps.readChunk(1000).asSomeError.flatMap { chunk =>
            if (chunk.isEmpty) ZIO.fail(None) else ZIO.succeed(chunk)
          }
        }

        // apply decoding pipeline
        val charStream: ZStream[Any, Exception, Char] =
          inStream.via(charset.newDecoder.transducer())

        charStream.runForeachChunk(chars => Console.print(chars.mkString))
      }
    }

}
