package zio.nio
package file

import zio.ZIO.attemptBlockingIO
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Scope, Trace, UIO, ZIO}

import java.io.IOException
import java.net.URI
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.{file => jf}
import scala.jdk.CollectionConverters._

final class FileSystem private (private val javaFileSystem: jf.FileSystem) extends IOCloseable {

  def provider: jf.spi.FileSystemProvider = javaFileSystem.provider()

  def close(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(javaFileSystem.close()).refineToOrDie[IOException]

  def isOpen(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(javaFileSystem.isOpen())

  def isReadOnly: Boolean = javaFileSystem.isReadOnly

  def getSeparator: String = javaFileSystem.getSeparator

  def getRootDirectories(implicit trace: Trace): UIO[List[Path]] =
    ZIO.succeed(javaFileSystem.getRootDirectories.asScala.map(Path.fromJava).toList)

  def getFileStores(implicit trace: Trace): UIO[List[jf.FileStore]] =
    ZIO.succeed(javaFileSystem.getFileStores.asScala.toList)

  def supportedFileAttributeViews: Set[String] = javaFileSystem.supportedFileAttributeViews().asScala.toSet

  def getPath(first: String, more: String*): Path = Path.fromJava(javaFileSystem.getPath(first, more: _*))

  def getPathMatcher(syntaxAndPattern: String): jf.PathMatcher = javaFileSystem.getPathMatcher(syntaxAndPattern)

  def getUserPrincipalLookupService: UserPrincipalLookupService = javaFileSystem.getUserPrincipalLookupService

  def newWatchService(implicit trace: Trace): ZIO[Scope, IOException, WatchService] =
    attemptBlockingIO(WatchService.fromJava(javaFileSystem.newWatchService())).toNioScoped

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

  def getFileSystem(uri: URI)(implicit trace: Trace): ZIO[Any, Exception, FileSystem] =
    attemptBlockingIO(new FileSystem(jf.FileSystems.getFileSystem(uri)))

  def newFileSystem(uri: URI, env: (String, Any)*)(implicit
    trace: Trace
  ): ZIO[Scope, IOException, FileSystem] =
    attemptBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(uri, env.toMap.asJava))).toNioScoped

  def newFileSystem(uri: URI, env: Map[String, _], loader: ClassLoader)(implicit
    trace: Trace
  ): ZIO[Scope, Exception, FileSystem] =
    attemptBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(uri, env.asJava, loader))).toNioScoped

  def newFileSystem(path: Path, loader: ClassLoader)(implicit
    trace: Trace
  ): ZIO[Scope, IOException, FileSystem] =
    attemptBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(path.javaPath, loader))).toNioScoped

}
