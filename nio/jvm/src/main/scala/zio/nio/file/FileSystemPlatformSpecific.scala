package zio.nio.file

import zio.nio.IOCloseableManagement
import zio.{Scope, Trace, ZIO}

import java.io.IOException
import java.nio.{file => jf}

trait FileSystemPlatformSpecific { self =>

  def jFileSystem: jf.FileSystem

  def newWatchService(implicit trace: Trace): ZIO[Scope, IOException, WatchService] =
    ZIO.attemptBlockingIO(WatchService.fromJava(jFileSystem.newWatchService())).toNioScoped
}
