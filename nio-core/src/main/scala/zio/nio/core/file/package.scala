package zio.nio.core

import java.nio.file.{ WatchEvent, Path => JPath }

package object file {

  implicit final class WatchEventOps[A](val javaEvent: WatchEvent[A]) extends AnyVal {

    /**
     * Returns the context of this `WatchEvent` as a path.
     *
     * This will return `None` only if this event's context is not a path,
     * but it seems there are no such cases in Java 8.
     */
    def asPath: Option[Path] =
      javaEvent.context() match {
        case javaPath: JPath => Some(Path.fromJava(javaPath))
        case _               => None
      }

  }

}
