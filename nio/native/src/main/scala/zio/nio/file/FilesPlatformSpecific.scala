package zio.nio.file

import java.nio.file.{Files => JFiles, SimpleFileVisitor, Path => JPath}
import java.io.IOException
import zio.{Trace, ZIO}
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitor

trait FilesPlatformSpecific {

  private val visitator: FileVisitor[JPath] = new SimpleFileVisitor[JPath]() {

    override def visitFile(file: JPath, attrs: BasicFileAttributes) = {
      JFiles.delete(file)
      FileVisitResult.CONTINUE
    }

    override def postVisitDirectory(dir: JPath, exc: IOException) = {
      JFiles.delete(dir)
      FileVisitResult.CONTINUE
    }

  }

  def deleteRecursive(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Long] =
    ZIO.attemptBlockingIO(JFiles.walkFileTree(path.javaPath, visitator)) *> ZIO.succeed(0L)

}
