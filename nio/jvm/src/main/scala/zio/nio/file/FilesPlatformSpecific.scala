package zio.nio.file

import zio.{Trace, ZIO}

import Files._
import zio.stream.ZSink
import java.io.IOException

trait FilesPlatformSpecific {

  def deleteRecursive(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Long] =
    newDirectoryStream(path).mapZIO { p =>
      for {
        deletedInSubDirectory <- deleteRecursive(p).whenZIO(isDirectory(p)).map(_.getOrElse(0L))
        deletedFile           <- deleteIfExists(p).whenZIO(isRegularFile(p)).map(_.getOrElse(false)).map(if (_) 1 else 0)
      } yield deletedInSubDirectory + deletedFile
    }
      .run(ZSink.sum) <* delete(path)

}
