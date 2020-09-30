package zio.nio.file

import java.io.IOException
import java.nio.file.{
  WatchEvent,
  Path => JPath,
  WatchKey => JWatchKey,
  WatchService => JWatchService,
  Watchable => JWatchable
}
import java.util.concurrent.TimeUnit

import zio._
import zio.blocking.Blocking
import zio.duration.Duration
import zio.nio.IOCloseable
import zio.stream.ZStream

import scala.jdk.CollectionConverters._

trait Watchable {
  protected def javaWatchable: JWatchable

  final def register(watcher: WatchService, events: WatchEvent.Kind[_]*): IO[IOException, WatchKey] =
    IO.effect(new WatchKey(javaWatchable.register(watcher.javaWatchService, events: _*))).refineToOrDie[IOException]

  final def register(
    watcher: WatchService,
    events: Iterable[WatchEvent.Kind[_]],
    modifiers: WatchEvent.Modifier*
  ): IO[IOException, WatchKey] =
    IO.effect(new WatchKey(javaWatchable.register(watcher.javaWatchService, events.toArray, modifiers: _*)))
      .refineToOrDie[IOException]
}

object Watchable {

  def apply(jWatchable: JWatchable): Watchable =
    new Watchable {
      override protected val javaWatchable = jWatchable
    }
}

/**
 * A token representing the registration of a watchable object with a `WatchService`.
 *
 * [[https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchKey.html Java NIO API]].
 */
final class WatchKey private[file] (private val javaKey: JWatchKey) {

  def isValid: UIO[Boolean] = UIO.effectTotal(javaKey.isValid)

  /**
   * Retrieves and removes all pending events for this watch key.
   *
   * This does not block, it will immediately return an empty list if there are no events pending.
   * Typically, this key should be reset after processing the returned events, the
   * `pollEventsManaged` method can be used to do this automatically and reliably.
   */
  def pollEvents: UIO[List[WatchEvent[_]]] = UIO.effectTotal(javaKey.pollEvents().asScala.toList)

  /**
   * Retrieves and removes all pending events for this watch key as a managed resource.
   *
   * This does not block, it will immediately return an empty list if there are no events pending.
   * When the returned `Managed` completed, this key will be '''reset'''.
   */
  def pollEventsManaged: Managed[Nothing, List[WatchEvent[_]]] = pollEvents.toManaged_.ensuring(reset)

  def reset: UIO[Boolean] = UIO.effectTotal(javaKey.reset())

  def cancel: UIO[Unit] = UIO.effectTotal(javaKey.cancel())

  def watchable: Watchable =
    javaKey.watchable() match {
      case javaPath: JPath => Path.fromJava(javaPath)
      case javaWatchable   => Watchable(javaWatchable)
    }

  /**
   * Convenience method to construct the complete path indicated by a `WatchEvent`.
   *
   * If both the following are true:
   * 1. This key's watchable is a filesystem path
   * 2. The event has a path as its context
   *
   * then this method returns a path with the event's path resolved against
   * this key's path, `(key path) / (event path)`.
   *
   * If either of the above conditions don't hold, `None` is returned.
   * The conditions will always hold when watching file system paths.
   */
  def resolveEventPath(event: WatchEvent[_]): Option[Path] =
    for {
      parent    <- javaKey.watchable() match {
                     case javaPath: JPath => Some(Path.fromJava(javaPath))
                     case _               => None
                   }
      eventPath <- event.asPath
    } yield parent / eventPath

}

/**
 * A watch service that watches registered objects for changes and events.
 *
 * For example a file manager may use a watch service to monitor a directory for changes so that it can update its
 * display of the list of files when files are created or deleted.
 *
 * Note if any of the methods, or a stream returned by the `stream` method, is used after this `WatchService`
 * has been closed, the operation will die with a `ClosedWatchServiceException`.
 */
final class WatchService private (private[file] val javaWatchService: JWatchService) extends IOCloseable {

  def close: IO[IOException, Unit] = IO.effect(javaWatchService.close()).refineToOrDie[IOException]

  def poll: UIO[Option[WatchKey]] =
    IO.effectTotal(Option(javaWatchService.poll()).map(new WatchKey(_)))

  def poll(timeout: Duration): URIO[Blocking, Option[WatchKey]] =
    blocking
      .effectBlockingInterrupt(
        Option(javaWatchService.poll(timeout.toNanos, TimeUnit.NANOSECONDS)).map(new WatchKey(_))
      )
      .orDie

  def take: URIO[Blocking, WatchKey] = blocking.effectBlockingInterrupt(new WatchKey(javaWatchService.take())).orDie

  /**
   * A stream of signalled objects which have pending events.
   *
   * Note the `WatchKey` objects returned by this stream must be reset before they will be
   * queued again with any additional events.
   */
  def stream: ZStream[Blocking, Nothing, WatchKey] =
    ZStream.repeatEffect {
      IO.effectTotal(new WatchKey(javaWatchService.take()))
    }

}

object WatchService {

  def forDefaultFileSystem: ZManaged[Blocking, IOException, WatchService] =
    FileSystem.default.newWatchService

  def fromJava(javaWatchService: JWatchService): WatchService = new WatchService(javaWatchService)

}
