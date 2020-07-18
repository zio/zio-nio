package zio.nio.core.file

import java.io.IOException
import java.nio.file.{
  ClosedWatchServiceException,
  WatchEvent,
  WatchKey => JWatchKey,
  WatchService => JWatchService,
  Watchable => JWatchable,
  Path => JPath
}
import java.util.concurrent.TimeUnit

import zio.blocking.Blocking
import zio.duration.Duration

import scala.jdk.CollectionConverters._
import zio.{ IO, UIO, ZIO }

trait Watchable {
  protected def javaWatchable: JWatchable

  final def register(watcher: WatchService, events: WatchEvent.Kind[_]*): IO[Exception, WatchKey] =
    IO.effect(new WatchKey(javaWatchable.register(watcher.javaWatchService, events: _*))).refineToOrDie[Exception]

  final def register(
    watcher: WatchService,
    events: Iterable[WatchEvent.Kind[_]],
    modifiers: WatchEvent.Modifier*
  ): IO[Exception, WatchKey] =
    IO.effect(new WatchKey(javaWatchable.register(watcher.javaWatchService, events.toArray, modifiers: _*)))
      .refineToOrDie[Exception]
}

object Watchable {

  def apply(jWatchable: JWatchable): Watchable =
    new Watchable {
      override protected val javaWatchable = jWatchable
    }
}

final class WatchKey private[file] (private val javaKey: JWatchKey) {
  def isValid: UIO[Boolean] = UIO.effectTotal(javaKey.isValid)

  def pollEvents: UIO[List[WatchEvent[_]]] = UIO.effectTotal(javaKey.pollEvents().asScala.toList)

  def reset: UIO[Boolean] = UIO.effectTotal(javaKey.reset())

  def cancel: UIO[Unit] = UIO.effectTotal(javaKey.cancel())

  def watchable: UIO[Watchable] =
    UIO.effectTotal(javaKey.watchable()).map {
      case javaPath: JPath => Path.fromJava(javaPath)
      case javaWatchable   => Watchable(javaWatchable)
    }
}

final class WatchService private (private[file] val javaWatchService: JWatchService) {
  def close: IO[IOException, Unit] = IO.effect(javaWatchService.close()).refineToOrDie[IOException]

  def poll: IO[ClosedWatchServiceException, Option[WatchKey]] =
    IO.effect(Option(javaWatchService.poll()).map(new WatchKey(_))).refineToOrDie[ClosedWatchServiceException]

  def poll(timeout: Duration): IO[Exception, Option[WatchKey]] =
    IO.effect(Option(javaWatchService.poll(timeout.toNanos, TimeUnit.NANOSECONDS)).map(new WatchKey(_)))
      .refineToOrDie[Exception]

  def take: ZIO[Blocking, Exception, WatchKey] =
    ZIO
      .accessM[Blocking](_.get.effectBlocking(new WatchKey(javaWatchService.take())))
      .refineToOrDie[Exception]
}

object WatchService {
  def forDefaultFileSystem: ZIO[Blocking, Exception, WatchService] = FileSystem.default.newWatchService

  def fromJava(javaWatchService: JWatchService): WatchService = new WatchService(javaWatchService)
}
