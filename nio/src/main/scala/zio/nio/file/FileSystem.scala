package zio.nio
package file

import zio.ZIO.attemptBlockingIO
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, UIO, Scope, ZIO, ZTraceElement}

import java.io.IOException
import java.net.URI
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.{file => jf}
import scala.jdk.CollectionConverters._

final class FileSystem private (private val javaFileSystem: jf.FileSystem) extends IOCloseable {

  def provider: jf.spi.FileSystemProvider = javaFileSystem.provider()

  def close(implicit trace: ZTraceElement): IO[IOException, Unit] =
    IO.attempt(javaFileSystem.close()).refineToOrDie[IOException]

  def isOpen(implicit trace: ZTraceElement): UIO[Boolean] = UIO.succeed(javaFileSystem.isOpen())

  def isReadOnly: Boolean = javaFileSystem.isReadOnly

  def getSeparator: String = javaFileSystem.getSeparator

  def getRootDirectories(implicit trace: ZTraceElement): UIO[List[Path]] =
    UIO.succeed(javaFileSystem.getRootDirectories.asScala.map(Path.fromJava).toList)

  def getFileStores(implicit trace: ZTraceElement): UIO[List[jf.FileStore]] =
    UIO.succeed(javaFileSystem.getFileStores.asScala.toList)

  def supportedFileAttributeViews: Set[String] = javaFileSystem.supportedFileAttributeViews().asScala.toSet

  def getPath(first: String, more: String*): Path = Path.fromJava(javaFileSystem.getPath(first, more: _*))

  def getPathMatcher(syntaxAndPattern: String): jf.PathMatcher = javaFileSystem.getPathMatcher(syntaxAndPattern)

  def getUserPrincipalLookupService: UserPrincipalLookupService = javaFileSystem.getUserPrincipalLookupService

  def newWatchService(implicit trace: ZTraceElement): ZIO[Scope, IOException, WatchService] =
    attemptBlockingIO(WatchService.fromJava(javaFileSystem.newWatchService())).toNioManaged

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

  def getFileSystem(uri: URI)(implicit trace: ZTraceElement): ZIO[Any, Exception, FileSystem] =
    attemptBlockingIO(new FileSystem(jf.FileSystems.getFileSystem(uri)))

  def newFileSystem(uri: URI, env: (String, Any)*)(implicit
    trace: ZTraceElement
  ): ZIO[Scope, IOException, FileSystem] =
    attemptBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(uri, env.toMap.asJava))).toNioManaged

  def newFileSystem(uri: URI, env: Map[String, _], loader: ClassLoader)(implicit
    trace: ZTraceElement
  ): ZIO[Scope, Exception, FileSystem] =
    attemptBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(uri, env.asJava, loader))).toNioManaged

  def newFileSystem(path: Path, loader: ClassLoader)(implicit
    trace: ZTraceElement
  ): ZIO[Scope, IOException, FileSystem] =
    attemptBlockingIO(new FileSystem(jf.FileSystems.newFileSystem(path.javaPath, loader))).toNioManaged

}
