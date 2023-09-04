package zio.nio.file

import java.nio.{file => jf}

trait FileSystemPlatformSpecific { self =>
  def jFileSystem: jf.FileSystem
}
