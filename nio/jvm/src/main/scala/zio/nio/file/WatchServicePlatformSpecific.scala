package zio.nio.file

import zio._

import java.io.IOException

trait WatchServicePlatformSpecific {

  def forDefaultFileSystem(implicit trace: Trace): ZIO[Scope, IOException, WatchService] =
    FileSystem.default.newWatchService

}
