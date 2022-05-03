package zio.nio.file

import zio.ZIO.attemptBlocking
import zio.nio.charset.Charset
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.{ZSink, ZStream}
import zio.{Chunk, Scope, Trace, ZIO}

import java.io.IOException
import java.nio.file.attribute._
import java.nio.file.{
  CopyOption,
  DirectoryStream,
  FileStore,
  FileVisitOption,
  Files => JFiles,
  LinkOption,
  OpenOption,
  Path => JPath
}
import java.util.function.BiPredicate
import scala.jdk.CollectionConverters._
import scala.reflect._

object Files {

  def newDirectoryStream(dir: Path, glob: String = "*")(implicit
    trace: Trace
  ): ZStream[Any, IOException, Path] = {
    val scoped = ZIO
      .fromAutoCloseable(attemptBlocking(JFiles.newDirectoryStream(dir.javaPath, glob)))
      .map(_.iterator())
    ZStream.fromJavaIteratorScoped(scoped).map(Path.fromJava).refineToOrDie[IOException]
  }

  def newDirectoryStream(dir: Path, filter: Path => Boolean)(implicit
    trace: Trace
  ): ZStream[Any, IOException, Path] = {
    val javaFilter: DirectoryStream.Filter[_ >: JPath] = javaPath => filter(Path.fromJava(javaPath))
    val scoped = ZIO
      .fromAutoCloseable(attemptBlocking(JFiles.newDirectoryStream(dir.javaPath, javaFilter)))
      .map(_.iterator())
    ZStream.fromJavaIteratorScoped(scoped).map(Path.fromJava).refineToOrDie[IOException]
  }

  def createFile(path: Path, attrs: FileAttribute[_]*)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.createFile(path.javaPath, attrs: _*)).unit.refineToOrDie[IOException]

  def createDirectory(path: Path, attrs: FileAttribute[_]*)(implicit
    trace: Trace
  ): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.createDirectory(path.javaPath, attrs: _*)).unit.refineToOrDie[IOException]

  def createDirectories(path: Path, attrs: FileAttribute[_]*)(implicit
    trace: Trace
  ): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.createDirectories(path.javaPath, attrs: _*)).unit.refineToOrDie[IOException]

  def createTempFileIn(
    dir: Path,
    suffix: String = ".tmp",
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  )(implicit trace: Trace): ZIO[Any, IOException, Path] =
    attemptBlocking(Path.fromJava(JFiles.createTempFile(dir.javaPath, prefix.orNull, suffix, fileAttributes.toSeq: _*)))
      .refineToOrDie[IOException]

  def createTempFileInScoped(
    dir: Path,
    suffix: String = ".tmp",
    prefix: Option[String] = None,
    fileAttributes: Iterable[FileAttribute[_]] = Nil
  )(implicit trace: Trace): ZIO[Scope, IOException, Path] =
    ZIO.acquireRelease(createTempFileIn(dir, suffix, prefix, fileAttributes))(release = deleteIfExists(_).ignore)

  def createTempFile(
    suffix: String = ".tmp",
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  )(implicit trace: Trace): ZIO[Any, IOException, Path] =
    attemptBlocking(Path.fromJava(JFiles.createTempFile(prefix.orNull, suffix, fileAttributes.toSeq: _*)))
      .refineToOrDie[IOException]

  def createTempFileScoped(
    suffix: String = ".tmp",
    prefix: Option[String] = None,
    fileAttributes: Iterable[FileAttribute[_]] = Nil
  )(implicit trace: Trace): ZIO[Scope, IOException, Path] =
    ZIO.acquireRelease(createTempFile(suffix, prefix, fileAttributes))(release = deleteIfExists(_).ignore)

  def createTempDirectory(
    dir: Path,
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  )(implicit trace: Trace): ZIO[Any, IOException, Path] =
    attemptBlocking(Path.fromJava(JFiles.createTempDirectory(dir.javaPath, prefix.orNull, fileAttributes.toSeq: _*)))
      .refineToOrDie[IOException]

  def createTempDirectoryScoped(
    dir: Path,
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  )(implicit trace: Trace): ZIO[Scope, IOException, Path] =
    ZIO.acquireRelease(createTempDirectory(dir, prefix, fileAttributes))(release = deleteRecursive(_).ignore)

  def createTempDirectory(
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  )(implicit trace: Trace): ZIO[Any, IOException, Path] =
    attemptBlocking(Path.fromJava(JFiles.createTempDirectory(prefix.orNull, fileAttributes.toSeq: _*)))
      .refineToOrDie[IOException]

  def createTempDirectoryScoped(
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  )(implicit trace: Trace): ZIO[Scope, IOException, Path] =
    ZIO.acquireRelease(createTempDirectory(prefix, fileAttributes))(release = deleteRecursive(_).ignore)

  def createSymbolicLink(
    link: Path,
    target: Path,
    fileAttributes: FileAttribute[_]*
  )(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.createSymbolicLink(link.javaPath, target.javaPath, fileAttributes: _*)).unit
      .refineToOrDie[IOException]

  def createLink(link: Path, existing: Path)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.createLink(link.javaPath, existing.javaPath)).unit.refineToOrDie[IOException]

  def delete(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.delete(path.javaPath)).refineToOrDie[IOException]

  def deleteIfExists(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Boolean] =
    attemptBlocking(JFiles.deleteIfExists(path.javaPath)).refineToOrDie[IOException]

  def deleteRecursive(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Long] =
    newDirectoryStream(path).mapZIO(delete).run(ZSink.count) <* delete(path)

  def copy(source: Path, target: Path, copyOptions: CopyOption*)(implicit
    trace: Trace
  ): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.copy(source.javaPath, target.javaPath, copyOptions: _*)).unit
      .refineToOrDie[IOException]

  def move(source: Path, target: Path, copyOptions: CopyOption*)(implicit
    trace: Trace
  ): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.move(source.javaPath, target.javaPath, copyOptions: _*)).unit.refineToOrDie[IOException]

  def readSymbolicLink(link: Path)(implicit trace: Trace): ZIO[Any, IOException, Path] =
    attemptBlocking(Path.fromJava(JFiles.readSymbolicLink(link.javaPath))).refineToOrDie[IOException]

  def getFileStore(path: Path)(implicit trace: Trace): ZIO[Any, IOException, FileStore] =
    attemptBlocking(JFiles.getFileStore(path.javaPath)).refineToOrDie[IOException]

  def isSameFile(path: Path, path2: Path)(implicit trace: Trace): ZIO[Any, IOException, Boolean] =
    attemptBlocking(JFiles.isSameFile(path.javaPath, path2.javaPath)).refineToOrDie[IOException]

  def isHidden(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Boolean] =
    attemptBlocking(JFiles.isHidden(path.javaPath)).refineToOrDie[IOException]

  def probeContentType(path: Path)(implicit trace: Trace): ZIO[Any, IOException, String] =
    attemptBlocking(JFiles.probeContentType(path.javaPath)).refineToOrDie[IOException]

  def useFileAttributeView[A <: FileAttributeView: ClassTag, B, E](path: Path, linkOptions: LinkOption*)(
    f: A => ZIO[Any, E, B]
  )(implicit trace: Trace): ZIO[Any, E, B] = {
    val viewClass =
      classTag[A].runtimeClass.asInstanceOf[Class[A]] // safe? because we know A is a subtype of FileAttributeView
    attemptBlocking(JFiles.getFileAttributeView[A](path.javaPath, viewClass, linkOptions: _*)).orDie
      .flatMap(f)
  }

  def readAttributes[A <: BasicFileAttributes: ClassTag](
    path: Path,
    linkOptions: LinkOption*
  )(implicit trace: Trace): ZIO[Any, IOException, A] = {
    // safe? because we know A is a subtype of BasicFileAttributes
    val attributeClass = classTag[A].runtimeClass.asInstanceOf[Class[A]]
    attemptBlocking(JFiles.readAttributes(path.javaPath, attributeClass, linkOptions: _*))
      .refineToOrDie[IOException]
  }

  final case class Attribute(attributeName: String, viewName: String = "basic") {
    def toJava: String = s"$viewName:$attributeName"
  }

  object Attribute {

    def fromJava(javaAttribute: String): Option[Attribute] =
      javaAttribute.split(':').toList match {
        case name :: Nil         => Some(Attribute(name))
        case view :: name :: Nil => Some(Attribute(name, view))
        case _                   => None
      }

  }

  def setAttribute(
    path: Path,
    attribute: Attribute,
    value: Object,
    linkOptions: LinkOption*
  )(implicit trace: Trace): ZIO[Any, Exception, Unit] =
    attemptBlocking(JFiles.setAttribute(path.javaPath, attribute.toJava, value, linkOptions: _*)).unit
      .refineToOrDie[Exception]

  def getAttribute(path: Path, attribute: Attribute, linkOptions: LinkOption*)(implicit
    trace: Trace
  ): ZIO[Any, IOException, Object] =
    attemptBlocking(JFiles.getAttribute(path.javaPath, attribute.toJava, linkOptions: _*)).refineToOrDie[IOException]

  sealed trait AttributeNames {

    def toJava: String =
      this match {
        case AttributeNames.All         => "*"
        case AttributeNames.List(names) => names.mkString(",")
      }

  }

  object AttributeNames {
    final case class List(names: scala.List[String]) extends AttributeNames

    case object All extends AttributeNames

    def fromJava(javaNames: String): AttributeNames =
      javaNames.trim match {
        case "*"  => All
        case list => List(list.split(',').toList)
      }

  }

  final case class Attributes(attributeNames: AttributeNames, viewName: String = "base") {
    def toJava: String = s"$viewName:${attributeNames.toJava}"
  }

  object Attributes {

    def fromJava(javaAttributes: String): Option[Attributes] =
      javaAttributes.split(':').toList match {
        case names :: Nil         => Some(Attributes(AttributeNames.fromJava(names)))
        case view :: names :: Nil => Some(Attributes(AttributeNames.fromJava(names), view))
        case _                    => None
      }

  }

  def readAttributes(
    path: Path,
    attributes: Attributes,
    linkOptions: LinkOption*
  )(implicit trace: Trace): ZIO[Any, IOException, Map[String, AnyRef]] =
    attemptBlocking(JFiles.readAttributes(path.javaPath, attributes.toJava, linkOptions: _*))
      .map(_.asScala.toMap)
      .refineToOrDie[IOException]

  def getPosixFilePermissions(
    path: Path,
    linkOptions: LinkOption*
  )(implicit trace: Trace): ZIO[Any, IOException, Set[PosixFilePermission]] =
    attemptBlocking(JFiles.getPosixFilePermissions(path.javaPath, linkOptions: _*))
      .map(_.asScala.toSet)
      .refineToOrDie[IOException]

  def setPosixFilePermissions(path: Path, permissions: Set[PosixFilePermission])(implicit
    trace: Trace
  ): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.setPosixFilePermissions(path.javaPath, permissions.asJava)).unit
      .refineToOrDie[IOException]

  def getOwner(path: Path, linkOptions: LinkOption*)(implicit
    trace: Trace
  ): ZIO[Any, IOException, UserPrincipal] =
    attemptBlocking(JFiles.getOwner(path.javaPath, linkOptions: _*)).refineToOrDie[IOException]

  def setOwner(path: Path, owner: UserPrincipal)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.setOwner(path.javaPath, owner)).unit.refineToOrDie[IOException]

  def isSymbolicLink(path: Path)(implicit trace: Trace): ZIO[Any, Nothing, Boolean] =
    attemptBlocking(JFiles.isSymbolicLink(path.javaPath)).orDie

  def isDirectory(path: Path, linkOptions: LinkOption*)(implicit trace: Trace): ZIO[Any, Nothing, Boolean] =
    attemptBlocking(JFiles.isDirectory(path.javaPath, linkOptions: _*)).orDie

  def isRegularFile(path: Path, linkOptions: LinkOption*)(implicit trace: Trace): ZIO[Any, Nothing, Boolean] =
    attemptBlocking(JFiles.isRegularFile(path.javaPath, linkOptions: _*)).orDie

  def getLastModifiedTime(path: Path, linkOptions: LinkOption*)(implicit
    trace: Trace
  ): ZIO[Any, IOException, FileTime] =
    attemptBlocking(JFiles.getLastModifiedTime(path.javaPath, linkOptions: _*)).refineToOrDie[IOException]

  def setLastModifiedTime(path: Path, time: FileTime)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.setLastModifiedTime(path.javaPath, time)).unit.refineToOrDie[IOException]

  def size(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Long] =
    attemptBlocking(JFiles.size(path.javaPath)).refineToOrDie[IOException]

  def exists(path: Path, linkOptions: LinkOption*)(implicit trace: Trace): ZIO[Any, Nothing, Boolean] =
    attemptBlocking(JFiles.exists(path.javaPath, linkOptions: _*)).orDie

  def notExists(path: Path, linkOptions: LinkOption*)(implicit trace: Trace): ZIO[Any, Nothing, Boolean] =
    attemptBlocking(JFiles.notExists(path.javaPath, linkOptions: _*)).orDie

  def isReadable(path: Path)(implicit trace: Trace): ZIO[Any, Nothing, Boolean] = attemptBlocking(
    JFiles.isReadable(path.javaPath)
  ).orDie

  def isWritable(path: Path)(implicit trace: Trace): ZIO[Any, Nothing, Boolean] = attemptBlocking(
    JFiles.isWritable(path.javaPath)
  ).orDie

  def isExecutable(path: Path)(implicit trace: Trace): ZIO[Any, Nothing, Boolean] =
    attemptBlocking(JFiles.isExecutable(path.javaPath)).orDie

  def readAllBytes(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Chunk[Byte]] =
    attemptBlocking(Chunk.fromArray(JFiles.readAllBytes(path.javaPath))).refineToOrDie[IOException]

  def readAllLines(path: Path, charset: Charset = Charset.Standard.utf8)(implicit
    trace: Trace
  ): ZIO[Any, IOException, List[String]] =
    attemptBlocking(JFiles.readAllLines(path.javaPath, charset.javaCharset).asScala.toList).refineToOrDie[IOException]

  def writeBytes(path: Path, bytes: Chunk[Byte], openOptions: OpenOption*)(implicit
    trace: Trace
  ): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.write(path.javaPath, bytes.toArray, openOptions: _*)).unit.refineToOrDie[IOException]

  def writeLines(
    path: Path,
    lines: Iterable[CharSequence],
    charset: Charset = Charset.Standard.utf8,
    openOptions: Set[OpenOption] = Set.empty
  )(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    attemptBlocking(JFiles.write(path.javaPath, lines.asJava, charset.javaCharset, openOptions.toSeq: _*)).unit
      .refineToOrDie[IOException]

  def lines(path: Path, charset: Charset = Charset.Standard.utf8)(implicit
    trace: Trace
  ): ZStream[Any, IOException, String] =
    ZStream
      .fromJavaStreamScoped(
        ZIO.fromAutoCloseable(attemptBlocking(JFiles.lines(path.javaPath, charset.javaCharset)))
      )
      .refineToOrDie[IOException]

  def list(path: Path)(implicit trace: Trace): ZStream[Any, IOException, Path] =
    ZStream
      .fromJavaStreamScoped(
        ZIO.fromAutoCloseable(attemptBlocking(JFiles.list(path.javaPath)))
      )
      .map(Path.fromJava)
      .refineToOrDie[IOException]

  def walk(
    path: Path,
    maxDepth: Int = Int.MaxValue,
    visitOptions: Set[FileVisitOption] = Set.empty
  )(implicit trace: Trace): ZStream[Any, IOException, Path] =
    ZStream
      .fromJavaStreamScoped(
        ZIO.fromAutoCloseable(attemptBlocking(JFiles.walk(path.javaPath, maxDepth, visitOptions.toSeq: _*)))
      )
      .map(Path.fromJava)
      .refineToOrDie[IOException]

  def find(path: Path, maxDepth: Int = Int.MaxValue, visitOptions: Set[FileVisitOption] = Set.empty)(
    test: (Path, BasicFileAttributes) => Boolean
  )(implicit trace: Trace): ZStream[Any, IOException, Path] = {
    val matcher: BiPredicate[JPath, BasicFileAttributes] = (path, attr) => test(Path.fromJava(path), attr)
    ZStream
      .fromJavaStreamScoped(
        ZIO.fromAutoCloseable(
          attemptBlocking(JFiles.find(path.javaPath, maxDepth, matcher, visitOptions.toSeq: _*))
        )
      )
      .map(Path.fromJava)
      .refineToOrDie[IOException]
  }

  def copy(
    in: ZStream[Any, IOException, Byte],
    target: Path,
    options: CopyOption*
  )(implicit trace: Trace): ZIO[Any, IOException, Long] =
    ZIO.scoped {
      in.toInputStream
        .flatMap(inputStream => attemptBlocking(JFiles.copy(inputStream, target.javaPath, options: _*)))
        .refineToOrDie[IOException]
    }

}
