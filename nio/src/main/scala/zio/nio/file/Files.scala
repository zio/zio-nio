package zio.nio.file

import java.io.IOException
import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.attribute._
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
import java.util.function.BiPredicate
import java.util.{ Iterator => JIterator }

import zio.blocking._
import zio.nio.core.file.Path
import zio.stream.ZStream
import zio.{ Chunk, UIO, ZIO, ZManaged }

import scala.jdk.CollectionConverters._
import scala.reflect._

object Files {

  def fromJavaIterator[A](iterator: JIterator[A]): ZStream[Blocking, RuntimeException, A] =
    ZStream.unfoldM(()) { _ =>
      effectBlocking {
        if (iterator.hasNext) Some((iterator.next(), ())) else None
      }.refineToOrDie[RuntimeException]
    }

  def newDirectoryStream(dir: Path, glob: String = "*"): ZStream[Blocking, Exception, Path] = {
    val managed = ZManaged.fromAutoCloseable(
      effectBlocking(JFiles.newDirectoryStream(dir.javaPath, glob)).refineToOrDie[Exception]
    )
    ZStream.managed(managed).mapM(dirStream => UIO(dirStream.iterator())).flatMap(fromJavaIterator).map(Path.fromJava)
  }

  def newDirectoryStream(dir: Path, filter: Path => Boolean): ZStream[Blocking, Exception, Path] = {
    val javaFilter: DirectoryStream.Filter[_ >: JPath] = javaPath => filter(Path.fromJava(javaPath))
    val managed                                        = ZManaged.fromAutoCloseable(
      effectBlocking(JFiles.newDirectoryStream(dir.javaPath, javaFilter)).refineToOrDie[Exception]
    )
    ZStream.managed(managed).mapM(dirStream => UIO(dirStream.iterator())).flatMap(fromJavaIterator).map(Path.fromJava)
  }

  def createFile(path: Path, attrs: FileAttribute[_]*): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.createFile(path.javaPath, attrs: _*)).unit.refineToOrDie[Exception]

  def createDirectory(path: Path, attrs: FileAttribute[_]*): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.createDirectory(path.javaPath, attrs: _*)).unit.refineToOrDie[Exception]

  def createDirectories(path: Path, attrs: FileAttribute[_]*): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.createDirectories(path.javaPath, attrs: _*)).unit.refineToOrDie[Exception]

  def createTempFileIn(
    dir: Path,
    suffix: String = ".tmp",
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  ): ZIO[Blocking, Exception, Path] =
    effectBlocking(Path.fromJava(JFiles.createTempFile(dir.javaPath, prefix.orNull, suffix, fileAttributes.toSeq: _*)))
      .refineToOrDie[Exception]

  def createTempFile(
    suffix: String = ".tmp",
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  ): ZIO[Blocking, Exception, Path] =
    effectBlocking(Path.fromJava(JFiles.createTempFile(prefix.orNull, suffix, fileAttributes.toSeq: _*)))
      .refineToOrDie[Exception]

  def createTempDirectory(
    dir: Path,
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  ): ZIO[Blocking, Exception, Path] =
    effectBlocking(Path.fromJava(JFiles.createTempDirectory(dir.javaPath, prefix.orNull, fileAttributes.toSeq: _*)))
      .refineToOrDie[Exception]

  def createTempDirectory(
    prefix: Option[String],
    fileAttributes: Iterable[FileAttribute[_]]
  ): ZIO[Blocking, Exception, Path] =
    effectBlocking(Path.fromJava(JFiles.createTempDirectory(prefix.orNull, fileAttributes.toSeq: _*)))
      .refineToOrDie[Exception]

  def createSymbolicLink(link: Path, target: Path, fileAttributes: FileAttribute[_]*): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.createSymbolicLink(link.javaPath, target.javaPath, fileAttributes: _*)).unit
      .refineToOrDie[Exception]

  def createLink(link: Path, existing: Path): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.createLink(link.javaPath, existing.javaPath)).unit.refineToOrDie[Exception]

  def delete(path: Path): ZIO[Blocking, IOException, Unit] =
    effectBlocking(JFiles.delete(path.javaPath)).refineToOrDie[IOException]

  def deleteIfExists(path: Path): ZIO[Blocking, IOException, Boolean] =
    effectBlocking(JFiles.deleteIfExists(path.javaPath)).refineToOrDie[IOException]

  def copy(source: Path, target: Path, copyOptions: CopyOption*): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.copy(source.javaPath, target.javaPath, copyOptions: _*)).unit
      .refineToOrDie[Exception]

  def move(source: Path, target: Path, copyOptions: CopyOption*): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.move(source.javaPath, target.javaPath, copyOptions: _*)).unit.refineToOrDie[Exception]

  def readSymbolicLink(link: Path): ZIO[Blocking, Exception, Path] =
    effectBlocking(Path.fromJava(JFiles.readSymbolicLink(link.javaPath))).refineToOrDie[Exception]

  def getFileStore(path: Path): ZIO[Blocking, IOException, FileStore] =
    effectBlocking(JFiles.getFileStore(path.javaPath)).refineToOrDie[IOException]

  def isSameFile(path: Path, path2: Path): ZIO[Blocking, IOException, Boolean] =
    effectBlocking(JFiles.isSameFile(path.javaPath, path2.javaPath)).refineToOrDie[IOException]

  def isHidden(path: Path): ZIO[Blocking, IOException, Boolean] =
    effectBlocking(JFiles.isHidden(path.javaPath)).refineToOrDie[IOException]

  def probeContentType(path: Path): ZIO[Blocking, IOException, String] =
    effectBlocking(JFiles.probeContentType(path.javaPath)).refineToOrDie[IOException]

  def useFileAttributeView[A <: FileAttributeView: ClassTag, B](path: Path, linkOptions: LinkOption*)(
    f: A => ZIO[Blocking, Exception, B]
  ): ZIO[Blocking, Exception, B] = {
    val viewClass =
      classTag[A].runtimeClass.asInstanceOf[Class[A]] // safe? because we know A is a subtype of FileAttributeView
    effectBlocking(JFiles.getFileAttributeView[A](path.javaPath, viewClass, linkOptions: _*)).orDie
      .flatMap(f)
  }

  def readAttributes[A <: BasicFileAttributes: ClassTag](
    path: Path,
    linkOptions: LinkOption*
  ): ZIO[Blocking, Exception, A] = {
    // safe? because we know A is a subtype of BasicFileAttributes
    val attributeClass = classTag[A].runtimeClass.asInstanceOf[Class[A]]
    effectBlocking(JFiles.readAttributes(path.javaPath, attributeClass, linkOptions: _*))
      .refineToOrDie[Exception]
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

  def getAttribute(path: Path, attribute: Attribute, linkOptions: LinkOption*): ZIO[Blocking, Exception, Object] =
    effectBlocking(JFiles.getAttribute(path.javaPath, attribute.toJava, linkOptions: _*)).refineToOrDie[Exception]

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
  ): ZIO[Blocking, Exception, Map[String, AnyRef]] =
    effectBlocking(JFiles.readAttributes(path.javaPath, attributes.toJava, linkOptions: _*))
      .map(_.asScala.toMap)
      .refineToOrDie[Exception]

  def getPosixFilePermissions(
    path: Path,
    linkOptions: LinkOption*
  ): ZIO[Blocking, Exception, Set[PosixFilePermission]] =
    effectBlocking(JFiles.getPosixFilePermissions(path.javaPath, linkOptions: _*))
      .map(_.asScala.toSet)
      .refineToOrDie[Exception]

  def setPosixFilePermissions(path: Path, permissions: Set[PosixFilePermission]): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.setPosixFilePermissions(path.javaPath, permissions.asJava)).unit
      .refineToOrDie[Exception]

  def getOwner(path: Path, linkOptions: LinkOption*): ZIO[Blocking, Exception, UserPrincipal] =
    effectBlocking(JFiles.getOwner(path.javaPath, linkOptions: _*)).refineToOrDie[Exception]

  def setOwner(path: Path, owner: UserPrincipal): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.setOwner(path.javaPath, owner)).unit.refineToOrDie[Exception]

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

  def readAllLines(path: Path, charset: Charset = StandardCharsets.UTF_8): ZIO[Blocking, IOException, List[String]] =
    effectBlocking(JFiles.readAllLines(path.javaPath, charset).asScala.toList).refineToOrDie[IOException]

  def writeBytes(path: Path, bytes: Chunk[Byte], openOptions: OpenOption*): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.write(path.javaPath, bytes.toArray, openOptions: _*)).unit.refineToOrDie[Exception]

  def writeLines(
    path: Path,
    lines: Iterable[CharSequence],
    charset: Charset = StandardCharsets.UTF_8,
    openOptions: Set[OpenOption] = Set.empty
  ): ZIO[Blocking, Exception, Unit] =
    effectBlocking(JFiles.write(path.javaPath, lines.asJava, charset, openOptions.toSeq: _*)).unit
      .refineToOrDie[Exception]

  def list(path: Path): ZStream[Blocking, Exception, Path] =
    ZStream
      .fromJavaIteratorManaged(
        ZManaged
          .make(effectBlocking(JFiles.list(path.javaPath)))(stream => ZIO.effectTotal(stream.close()))
          .map(_.iterator())
      )
      .map(Path.fromJava)
      .refineOrDie {
        case io: IOException => io
      }

  def walk(
    path: Path,
    maxDepth: Int = Int.MaxValue,
    visitOptions: Set[FileVisitOption] = Set.empty
  ): ZStream[Blocking, Exception, Path] =
    ZStream
      .fromEffect(
        effectBlocking(JFiles.walk(path.javaPath, maxDepth, visitOptions.toSeq: _*).iterator())
          .refineToOrDie[IOException]
      )
      .flatMap(fromJavaIterator)
      .map(Path.fromJava)

  def find(path: Path, maxDepth: Int = Int.MaxValue, visitOptions: Set[FileVisitOption] = Set.empty)(
    test: (Path, BasicFileAttributes) => Boolean
  ): ZStream[Blocking, Exception, Path] = {
    val matcher: BiPredicate[JPath, BasicFileAttributes] = (path, attr) => test(Path.fromJava(path), attr)
    ZStream
      .fromEffect(
        effectBlocking(JFiles.find(path.javaPath, maxDepth, matcher, visitOptions.toSeq: _*).iterator())
          .refineToOrDie[IOException]
      )
      .flatMap(fromJavaIterator)
      .map(Path.fromJava)
  }

//
//  def copy(in: ZStream[Blocking, Exception, Chunk[Byte]], target: Path, options: CopyOption*): ZIO[Blocking, Exception, Long] = {
//
//    FileChannel.open(target).flatMap { channel =>
//      in.fold[Blocking, Exception, Chunk[Byte], Long].flatMap { startFold =>
//        val f = (count: Long, chunk: Chunk[Byte]) => {
//          channel.write(chunk).map(_ + count)
//        }
//        startFold(0L, Function.const(true), f)
//      }
//    }.use(ZIO.succeed)
//  }
}
