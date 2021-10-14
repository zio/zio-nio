package zio.nio
package file

import zio.blocking.{Blocking, effectBlockingIO}
import zio.{IO, UIO, ZIO, ZManaged}

import java.io.IOException
import java.net.URI
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.{file => jf}
import scala.jdk.CollectionConverters._

final class FileSystem private (private val javaFileSystem: jf.FileSystem) extends IOCloseable {

  def provider: jf.spi.FileSystemProvider = javaFileSystem.provider()

  def close: IO[IOException, Unit] = IO.effect(javaFileSystem.close()).refineToOrDie[IOException]

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
    effectBlockingIO(WatchService.fromJava(javaFileSystem.newWatchService())).toNioManaged

}

object FileSystem {
  def fromJava(javaFileSystem: jf.FileSystem): FileSystem = new FileSystem(javaFileSystem)

  /**
   * The default filesystem.
   *
   * The default file system creates objects that provide access to the file systems accessible to the Java virtual
   * machine. The working directory of the file system is the current user directory, named by the system property
   * user.dir.
   *
   * '''Note:''' The default filesystem cannot be closed, and if its `close` is called it will die with
   * `UnsupportedOperationException`. Therefore, do not use resource management with the default filesystem.
   */
  def default: FileSystem = new FileSystem(jf.FileSystems.getDefault)

  def getFileSystem(uri: URI): ZIO[Blocking, Exception, FileSystem] =
    effectBlockingIO(new FileSystem(jf.FileSystems.getFileSystem(uri)))

  def newFileSystem(uri: URI, env: (String, Any)*): ZManaged[Blocking, IOException, FileSystem] =
    effectBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(uri, env.toMap.asJava))).toNioManaged

  def newFileSystem(uri: URI, env: Map[String, _], loader: ClassLoader): ZManaged[Blocking, Exception, FileSystem] =
    effectBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(uri, env.asJava, loader))).toNioManaged

  def newFileSystem(path: Path, loader: ClassLoader): ZManaged[Blocking, IOException, FileSystem] =
    effectBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(path.javaPath, loader))).toNioManaged

}
