package zio.nio.core.file

import java.io.IOException
import java.net.URI
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.{ file => jf }

import zio.blocking.{ Blocking, effectBlocking }
import zio.{ UIO, ZIO }
import zio.nio.core.IOCloseable

import scala.jdk.CollectionConverters._

final class FileSystem private (private val javaFileSystem: jf.FileSystem) extends IOCloseable[Blocking] {
  def provider: jf.spi.FileSystemProvider = javaFileSystem.provider()

  override def close: ZIO[Blocking, IOException, Unit] =
    effectBlocking(javaFileSystem.close()).refineToOrDie[IOException]

  def isOpen: UIO[Boolean] = UIO.effectTotal(javaFileSystem.isOpen())

  def isReadOnly: Boolean = javaFileSystem.isReadOnly

  def getSeparator: String = javaFileSystem.getSeparator

  def getRootDirectories: UIO[List[Path]] =
    UIO.effectTotal(javaFileSystem.getRootDirectories.asScala.map(Path.fromJava).toList)

  def getFileStores: UIO[List[jf.FileStore]] = UIO.effectTotal(javaFileSystem.getFileStores.asScala.toList)

  def supportedFileAttributeViews: Set[String] = javaFileSystem.supportedFileAttributeViews().asScala.toSet

  def getPath(first: String, more: String*): Path = Path.fromJava(javaFileSystem.getPath(first, more: _*))

  def getPathMatcher(syntaxAndPattern: String): jf.PathMatcher = javaFileSystem.getPathMatcher(syntaxAndPattern)

  def getUserPrincipalLookupService: UserPrincipalLookupService = javaFileSystem.getUserPrincipalLookupService

  def newWatchService: ZIO[Blocking, IOException, WatchService] =
    effectBlocking(WatchService.fromJava(javaFileSystem.newWatchService())).refineToOrDie[IOException]
}

object FileSystem {
  def fromJava(javaFileSystem: jf.FileSystem): FileSystem = new FileSystem(javaFileSystem)

  def default: FileSystem = new FileSystem(jf.FileSystems.getDefault)

  def getFileSystem(uri: URI): ZIO[Blocking, Exception, FileSystem] =
    effectBlocking(new FileSystem(jf.FileSystems.getFileSystem(uri))).refineToOrDie[Exception]

  def newFileSystem(uri: URI, env: (String, Any)*): ZIO[Blocking, Exception, FileSystem] =
    effectBlocking(new FileSystem(jf.FileSystems.newFileSystem(uri, env.toMap.asJava)))
      .refineToOrDie[Exception]

  def newFileSystem(uri: URI, env: Map[String, _], loader: ClassLoader): ZIO[Blocking, Exception, FileSystem] =
    effectBlocking(new FileSystem(jf.FileSystems.newFileSystem(uri, env.asJava, loader)))
      .refineToOrDie[Exception]

  def newFileSystem(path: Path, loader: ClassLoader): ZIO[Blocking, Exception, FileSystem] =
    effectBlocking(new FileSystem(jf.FileSystems.newFileSystem(path.javaPath, loader)))
      .refineToOrDie[Exception]
}
