package zio.nio.file

import zio._
import zio.nio.IOCloseable
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.ZStream

import java.io.IOException
import java.nio.file.{
  Path => JPath,
  WatchEvent,
  WatchKey => JWatchKey,
  WatchService => JWatchService,
  Watchable => JWatchable
}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

trait Watchable {
  protected def javaWatchable: JWatchable

  final def register(watcher: WatchService, events: WatchEvent.Kind[_]*)(implicit
    trace: Trace
  ): IO[IOException, WatchKey] =
    ZIO.attempt(new WatchKey(javaWatchable.register(watcher.javaWatchService, events: _*))).refineToOrDie[IOException]

  final def register(
    watcher: WatchService,
    events: Iterable[WatchEvent.Kind[_]],
    modifiers: WatchEvent.Modifier*
  )(implicit trace: Trace): IO[IOException, WatchKey] =
    ZIO
      .attempt(new WatchKey(javaWatchable.register(watcher.javaWatchService, events.toArray, modifiers: _*)))
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

  def isValid(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(javaKey.isValid)

  /**
   * Retrieves and removes all pending events for this watch key.
   *
   * This does not block, it will immediately return an empty list if there are no events pending. Typically, this key
   * should be reset after processing the returned events, the `pollEventsScoped` method can be used to do this
   * automatically and reliably.
   */
  def pollEvents(implicit trace: Trace): UIO[List[WatchEvent[_]]] =
    ZIO.succeed(javaKey.pollEvents().asScala.toList)

  /**
   * Retrieves and removes all pending events for this watch key as a scoped resource.
   *
   * This does not block, it will immediately return an empty list if there are no events pending. When the `Scope` is
   * closed, this key will be '''reset'''.
   */
  def pollEventsScoped(implicit trace: Trace): ZIO[Scope, Nothing, List[WatchEvent[_]]] =
    pollEvents.withFinalizer(_ => reset)

  /**
   * Resets this watch key, making it eligible to be re-queued in the `WatchService`. A key is typically reset after all
   * the pending events retrieved from `pollEvents` have been processed. Use `pollEventsScop[ed` to automatically and
   * reliably perform a reset.
   */
  def reset(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(javaKey.reset())

  /**
   * Cancels the registration with the watch service. Upon return the watch key will be invalid. If the watch key is
   * enqueued, waiting to be retrieved from the watch service, then it will remain in the queue until it is removed.
   * Pending events, if any, remain pending and may be retrieved by invoking the pollEvents method after the key is
   * cancelled. If this watch key has already been cancelled then invoking this method has no effect. Once cancelled, a
   * watch key remains forever invalid.
   */
  def cancel(implicit trace: Trace): UIO[Unit] = ZIO.succeed(javaKey.cancel())

  /**
   * Returns the object for which this watch key was created.
   */
  def watchable: Watchable =
    javaKey.watchable() match {
      case javaPath: JPath => Path.fromJava(javaPath)
      case javaWatchable   => Watchable(javaWatchable)
    }

  /**
   * Convenience method to construct the complete path indicated by a `WatchEvent`.
   *
   * If both the following are true:
   *   1. This key's watchable is a filesystem path 2. The event has a path as its context
   *
   * then this method returns a path with the event's path resolved against this key's path, `(key path) / (event
   * path)`.
   *
   * If either of the above conditions don't hold, `None` is returned. The conditions will always hold when watching
   * file system paths.
   */
  def resolveEventPath(event: WatchEvent[_]): Option[Path] =
    for {
      parent <- javaKey.watchable() match {
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
 * Note if any of the methods, or a stream returned by the `stream` method, is used after this `WatchService` has been
 * closed, the operation will die with a `ClosedWatchServiceException`.
 */
final class WatchService private (private[file] val javaWatchService: JWatchService) extends IOCloseable {

  def close(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(javaWatchService.close()).refineToOrDie[IOException]

  def poll(implicit trace: Trace): UIO[Option[WatchKey]] =
    ZIO.succeed(Option(javaWatchService.poll()).map(new WatchKey(_)))

  def poll(timeout: Duration)(implicit trace: Trace): URIO[Any, Option[WatchKey]] =
    ZIO
      .attemptBlockingInterrupt(
        Option(javaWatchService.poll(timeout.toNanos, TimeUnit.NANOSECONDS)).map(new WatchKey(_))
      )
      .orDie

  /**
   * Retrieves and removes next watch key, waiting if none are yet present.
   */
  def take(implicit trace: Trace): URIO[Any, WatchKey] =
    ZIO.attemptBlockingInterrupt(new WatchKey(javaWatchService.take())).orDie

  /**
   * A stream of signalled objects which have pending events.
   *
   * Note the `WatchKey` objects returned by this stream must be reset before they will be queued again with any
   * additional events.
   */
  def stream(implicit trace: Trace): ZStream[Any, Nothing, WatchKey] = ZStream.repeatZIO(take)

}

object WatchService {

  def forDefaultFileSystem(implicit trace: Trace): ZIO[Scope, IOException, WatchService] =
    FileSystem.default.newWatchService

  def fromJava(javaWatchService: JWatchService): WatchService = new WatchService(javaWatchService)

}
