package zio.nio.core.file

import java.io.IOException
import java.nio.file.{
  WatchEvent,
  Path => JPath,
  WatchKey => JWatchKey,
  WatchService => JWatchService,
  Watchable => JWatchable
}
import java.util.concurrent.TimeUnit

import zio.blocking.Blocking
import zio.duration.Duration
import zio.nio.core.IOCloseable
import zio._

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

}

object WatchService {

  def forDefaultFileSystem: ZManaged[Blocking, IOException, WatchService] =
    FileSystem.default.newWatchService

  def fromJava(javaWatchService: JWatchService): WatchService = new WatchService(javaWatchService)

}
