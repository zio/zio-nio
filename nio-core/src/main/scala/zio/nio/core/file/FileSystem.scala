package zio.nio.core
package file

import java.io.IOException
import java.net.URI
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.{ file => jf }

import zio.blocking.{ Blocking, effectBlocking, effectBlockingIO }
import zio.{ UIO, ZIO, ZManaged }

import scala.jdk.CollectionConverters._

final class FileSystem private (private val javaFileSystem: jf.FileSystem) extends IOCloseable {

  type Env = Blocking

  def provider: jf.spi.FileSystemProvider = javaFileSystem.provider()

  def close: ZIO[Blocking, IOException, Unit] =
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

  def newWatchService: ZManaged[Blocking, IOException, WatchService] =
    effectBlocking(WatchService.fromJava(javaFileSystem.newWatchService())).refineToOrDie[IOException].toNioManaged
}

object FileSystem {
  def fromJava(javaFileSystem: jf.FileSystem): FileSystem = new FileSystem(javaFileSystem)

  def default: FileSystem = new FileSystem(jf.FileSystems.getDefault)

  def getFileSystem(uri: URI): ZIO[Blocking, Exception, FileSystem] =
    effectBlocking(new FileSystem(jf.FileSystems.getFileSystem(uri))).refineToOrDie[Exception]

  def newFileSystem(uri: URI, env: (String, Any)*): ZManaged[Blocking, IOException, FileSystem] =
    effectBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(uri, env.toMap.asJava))).toNioManaged

  def newFileSystem(uri: URI, env: Map[String, _], loader: ClassLoader): ZManaged[Blocking, Exception, FileSystem] =
    effectBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(uri, env.asJava, loader))).toNioManaged

  def newFileSystem(path: Path, loader: ClassLoader): ZManaged[Blocking, IOException, FileSystem] =
    effectBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(path.javaPath, loader))).toNioManaged
}
