package zio.nio
package file

import java.io.IOException
import java.nio.file.{
  CopyOption,
  DirectoryStream,
  FileStore,
  FileVisitOption,
  LinkOption,
  OpenOption,
  Files => JFiles,
  Path => JPath
}
import java.nio.file.attribute._
import java.util.function.BiPredicate

import zio.{ Chunk, ZIO, ZManaged }
import zio.blocking._
import zio.nio.core.ioExceptionOnly
import zio.nio.core.charset.Charset
import zio.nio.core.file.Path
import zio.stream.ZStream

import scala.jdk.CollectionConverters._
import scala.reflect._

object Files {

  def newDirectoryStream(dir: Path, glob: String = "*"): ZStream[Blocking, IOException, Path] = {
    val managed = ZManaged
      .fromAutoCloseable(effectBlocking(JFiles.newDirectoryStream(dir.javaPath, glob)))
      .map(_.iterator())
    ZStream.fromJavaIteratorManaged(managed).map(Path.fromJava).refineOrDie(ioExceptionOnly)
  }

  def newDirectoryStream(dir: Path, filter: Path => Boolean): ZStream[Blocking, IOException, Path] = {
    val javaFilter: DirectoryStream.Filter[_ >: JPath] = javaPath => filter(Path.fromJava(javaPath))
    val managed                                        = ZManaged
      .fromAutoCloseable(effectBlocking(JFiles.newDirectoryStream(dir.javaPath, javaFilter)))
      .map(_.iterator())
    ZStream.fromJavaIteratorManaged(managed).map(Path.fromJava).refineOrDie(ioExceptionOnly)
  }

  def createFile(path: Path, attrs: FileAttribute[_]*): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.createFile(path.javaPath, attrs: _*)).unit.refineToOrDie[IOException]

  def createDirectory(path: Path, attrs: FileAttribute[_]*): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.createDirectory(path.javaPath, attrs: _*)).unit.refineToOrDie[IOException]

  def createDirectories(path: Path, attrs: FileAttribute[_]*): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.createDirectories(path.javaPath, attrs: _*)).unit.refineToOrDie[IOException]

  def createTempFileIn(
    dir: Path,
    suffix: String = ".tmp",
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  ): ZIO[Blocking, IOException, Path] =
    effectBlocking(Path.fromJava(JFiles.createTempFile(dir.javaPath, prefix.orNull, suffix, fileAttributes.toSeq: _*)))
      .refineToOrDie[IOException]

  def createTempFile(
    suffix: String = ".tmp",
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  ): ZIO[Blocking, IOException, Path] =
    effectBlocking(Path.fromJava(JFiles.createTempFile(prefix.orNull, suffix, fileAttributes.toSeq: _*)))
      .refineToOrDie[IOException]

  def createTempDirectory(
    dir: Path,
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  ): ZIO[Blocking, IOException, Path] =
    effectBlocking(Path.fromJava(JFiles.createTempDirectory(dir.javaPath, prefix.orNull, fileAttributes.toSeq: _*)))
      .refineToOrDie[IOException]

  def createTempDirectory(
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  ): ZIO[Blocking, IOException, Path] =
    effectBlocking(Path.fromJava(JFiles.createTempDirectory(prefix.orNull, fileAttributes.toSeq: _*)))
      .refineToOrDie[IOException]

  def createSymbolicLink(
    link: Path,
    target: Path,
    fileAttributes: FileAttribute[_]*
  ): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.createSymbolicLink(link.javaPath, target.javaPath, fileAttributes: _*)).unit
      .refineToOrDie[IOException]

  def createLink(link: Path, existing: Path): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.createLink(link.javaPath, existing.javaPath)).unit.refineToOrDie[IOException]

  def delete(path: Path): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.delete(path.javaPath)).refineToOrDie[IOException]

  def deleteIfExists(path: Path): ZIO[Blocking, IOException, Boolean] =
    effectBlocking(JFiles.deleteIfExists(path.javaPath)).refineToOrDie[IOException]

  def copy(source: Path, target: Path, copyOptions: CopyOption*): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.copy(source.javaPath, target.javaPath, copyOptions: _*)).unit
      .refineToOrDie[IOException]

  def move(source: Path, target: Path, copyOptions: CopyOption*): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.move(source.javaPath, target.javaPath, copyOptions: _*)).unit.refineToOrDie[IOException]

  def readSymbolicLink(link: Path): ZIO[Blocking, IOException, Path] =
    effectBlocking(Path.fromJava(JFiles.readSymbolicLink(link.javaPath))).refineToOrDie[IOException]

  def getFileStore(path: Path): ZIO[Blocking, IOException, FileStore] =
    effectBlocking(JFiles.getFileStore(path.javaPath)).refineToOrDie[IOException]

  def isSameFile(path: Path, path2: Path): ZIO[Blocking, IOException, Boolean] =
    effectBlocking(JFiles.isSameFile(path.javaPath, path2.javaPath)).refineToOrDie[IOException]

  def isHidden(path: Path): ZIO[Blocking, IOException, Boolean] =
    effectBlocking(JFiles.isHidden(path.javaPath)).refineToOrDie[IOException]

  def probeContentType(path: Path): ZIO[Blocking, IOException, String] =
    effectBlocking(JFiles.probeContentType(path.javaPath)).refineToOrDie[IOException]

  def useFileAttributeView[A <: FileAttributeView: ClassTag, B, E](path: Path, linkOptions: LinkOption*)(
    f: A => ZIO[Blocking, E, B]
  ): ZIO[Blocking, E, B] = {
    val viewClass =
      classTag[A].runtimeClass.asInstanceOf[Class[A]] // safe? because we know A is a subtype of FileAttributeView
    effectBlocking(JFiles.getFileAttributeView[A](path.javaPath, viewClass, linkOptions: _*)).orDie
      .flatMap(f)
  }

  def readAttributes[A <: BasicFileAttributes: ClassTag](
    path: Path,
    linkOptions: LinkOption*
  ): ZIO[Blocking, IOException, A] = {
    // safe? because we know A is a subtype of BasicFileAttributes
    val attributeClass = classTag[A].runtimeClass.asInstanceOf[Class[A]]
    effectBlocking(JFiles.readAttributes(path.javaPath, attributeClass, linkOptions: _*))
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
  ): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.setAttribute(path.javaPath, attribute.toJava, value, linkOptions: _*)).unit
      .refineToOrDie[Exception]

  def getAttribute(path: Path, attribute: Attribute, linkOptions: LinkOption*): ZIO[Blocking, IOException, Object] =
    effectBlocking(JFiles.getAttribute(path.javaPath, attribute.toJava, linkOptions: _*)).refineToOrDie[IOException]

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
  ): ZIO[Blocking, IOException, Map[String, AnyRef]] =
    effectBlocking(JFiles.readAttributes(path.javaPath, attributes.toJava, linkOptions: _*))
      .map(_.asScala.toMap)
      .refineToOrDie[IOException]

  def getPosixFilePermissions(
    path: Path,
    linkOptions: LinkOption*
  ): ZIO[Blocking, IOException, Set[PosixFilePermission]] =
    effectBlocking(JFiles.getPosixFilePermissions(path.javaPath, linkOptions: _*))
      .map(_.asScala.toSet)
      .refineToOrDie[IOException]

  def setPosixFilePermissions(path: Path, permissions: Set[PosixFilePermission]): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.setPosixFilePermissions(path.javaPath, permissions.asJava)).unit
      .refineToOrDie[IOException]

  def getOwner(path: Path, linkOptions: LinkOption*): ZIO[Blocking, IOException, UserPrincipal] =
    effectBlocking(JFiles.getOwner(path.javaPath, linkOptions: _*)).refineToOrDie[IOException]

  def setOwner(path: Path, owner: UserPrincipal): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.setOwner(path.javaPath, owner)).unit.refineToOrDie[IOException]

  def isSymbolicLink(path: Path): ZIO[Blocking, Nothing, Boolean] =
    effectBlocking(JFiles.isSymbolicLink(path.javaPath)).orDie

  def isDirectory(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Nothing, Boolean] =
    effectBlocking(JFiles.isDirectory(path.javaPath, linkOptions: _*)).orDie

  def isRegularFile(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Nothing, Boolean] =
    effectBlocking(JFiles.isRegularFile(path.javaPath, linkOptions: _*)).orDie

  def getLastModifiedTime(path: Path, linkOptions: LinkOption*): ZIO[Blocking, IOException, FileTime] =
    effectBlocking(JFiles.getLastModifiedTime(path.javaPath, linkOptions: _*)).refineToOrDie[IOException]

  def setLastModifiedTime(path: Path, time: FileTime): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.setLastModifiedTime(path.javaPath, time)).unit.refineToOrDie[IOException]

  def size(path: Path): ZIO[Blocking, IOException, Long] =
    effectBlocking(JFiles.size(path.javaPath)).refineToOrDie[IOException]

  def exists(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Nothing, Boolean] =
    effectBlocking(JFiles.exists(path.javaPath, linkOptions: _*)).orDie

  def notExists(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Nothing, Boolean] =
    effectBlocking(JFiles.notExists(path.javaPath, linkOptions: _*)).orDie

  def isReadable(path: Path): ZIO[Blocking, Nothing, Boolean] =
    effectBlocking(JFiles.isReadable(path.javaPath)).orDie

  def isWritable(path: Path): ZIO[Blocking, Nothing, Boolean] =
    effectBlocking(JFiles.isWritable(path.javaPath)).orDie

  def isExecutable(path: Path): ZIO[Blocking, Nothing, Boolean] =
    effectBlocking(JFiles.isExecutable(path.javaPath)).orDie

  def readAllBytes(path: Path): ZIO[Blocking, IOException, Chunk[Byte]] =
    effectBlocking(Chunk.fromArray(JFiles.readAllBytes(path.javaPath))).refineToOrDie[IOException]

  def readAllLines(path: Path, charset: Charset = Charset.Standard.utf8): ZIO[Blocking, IOException, List[String]] =
    effectBlocking(JFiles.readAllLines(path.javaPath, charset.javaCharset).asScala.toList).refineToOrDie[IOException]

  def writeBytes(path: Path, bytes: Chunk[Byte], openOptions: OpenOption*): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.write(path.javaPath, bytes.toArray, openOptions: _*)).unit.refineToOrDie[IOException]

  def writeLines(
    path: Path,
    lines: Iterable[CharSequence],
    charset: Charset = Charset.Standard.utf8,
    openOptions: Set[OpenOption] = Set.empty
  ): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.write(path.javaPath, lines.asJava, charset.javaCharset, openOptions.toSeq: _*)).unit
      .refineToOrDie[IOException]

  def lines(path: Path, charset: Charset = Charset.Standard.utf8): ZStream[Blocking, IOException, String] =
    ZStream
      .fromJavaStreamManaged(
        ZManaged.fromAutoCloseable(effectBlocking(JFiles.lines(path.javaPath, charset.javaCharset)))
      )
      .refineOrDie(ioExceptionOnly)

  def list(path: Path): ZStream[Blocking, IOException, Path] =
    ZStream
      .fromJavaStreamManaged(
        ZManaged.fromAutoCloseable(effectBlocking(JFiles.list(path.javaPath)))
      )
      .map(Path.fromJava)
      .refineOrDie(ioExceptionOnly)

  def walk(
    path: Path,
    maxDepth: Int = Int.MaxValue,
    visitOptions: Set[FileVisitOption] = Set.empty
  ): ZStream[Blocking, IOException, Path] =
    ZStream
      .fromJavaStreamManaged(
        ZManaged.fromAutoCloseable(effectBlocking(JFiles.walk(path.javaPath, maxDepth, visitOptions.toSeq: _*)))
      )
      .map(Path.fromJava)
      .refineOrDie(ioExceptionOnly)

  def find(path: Path, maxDepth: Int = Int.MaxValue, visitOptions: Set[FileVisitOption] = Set.empty)(
    test: (Path, BasicFileAttributes) => Boolean
  ): ZStream[Blocking, IOException, Path] = {
    val matcher: BiPredicate[JPath, BasicFileAttributes] = (path, attr) => test(Path.fromJava(path), attr)
    ZStream
      .fromJavaStreamManaged(
        ZManaged.fromAutoCloseable(
          effectBlocking(JFiles.find(path.javaPath, maxDepth, matcher, visitOptions.toSeq: _*))
        )
      )
      .map(Path.fromJava)
      .refineOrDie(ioExceptionOnly)
  }

  def copy(
    in: ZStream[Blocking, IOException, Byte],
    target: Path,
    options: CopyOption*
  ): ZIO[Blocking, IOException, Long] =
    in.toInputStream
      .use(inputStream => effectBlocking(JFiles.copy(inputStream, target.javaPath, options: _*)))
      .refineToOrDie[IOException]

}
