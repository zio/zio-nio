package zio.nio.file

import zio.{Trace, ZIO, Scope}
import java.io.IOException
import java.nio.{file => jf}
import zio.nio.IOCloseableManagement

trait FileSystemPlatformSpecific { self =>

  def jFileSystem: jf.FileSystem

  def newWatchService(implicit trace: Trace): ZIO[Scope, IOException, WatchService] =
    ZIO.attemptBlockingIO(WatchService.fromJava(jFileSystem.newWatchService())).toNioScoped
}
