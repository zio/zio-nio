package scalaz.nio.channels

import java.nio.channels.{ AsynchronousChannelGroup => JAsynchronousChannelGroup }

import scalaz.zio.IO

class AsynchronousChannelGroup(val jChannelGroup: JAsynchronousChannelGroup) {}

object AsynchronousChannelGroup {

  def apply(): IO[Exception, AsynchronousChannelGroup] =
    ??? // IO.syncException { throw new Exception() }
}
