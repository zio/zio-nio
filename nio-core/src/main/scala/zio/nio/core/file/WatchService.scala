package zio.nio.core.file

import java.io.IOException
import java.nio.file.{
  WatchEvent,
  Path => JPath,
  Watchable => JWatchable,
  WatchKey => JWatchKey,
  WatchService => JWatchService
}
import java.util.concurrent.TimeUnit

import zio.blocking.Blocking
import zio.duration.Duration
import zio.{ IO, UIO, ZIO }
import zio.nio.core.IOCloseable

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

final class WatchService private (private[file] val javaWatchService: JWatchService) extends IOCloseable[Any] {

  override def close: IO[IOException, Unit] = IO.effect(javaWatchService.close()).refineToOrDie[IOException]

  def poll: UIO[Option[WatchKey]] =
    IO.effectTotal(Option(javaWatchService.poll()).map(new WatchKey(_)))

  def poll(timeout: Duration): IO[InterruptedException, Option[WatchKey]] =
    IO.effect(Option(javaWatchService.poll(timeout.toNanos, TimeUnit.NANOSECONDS)).map(new WatchKey(_)))
      .refineToOrDie[InterruptedException]

  def take: ZIO[Blocking, InterruptedException, WatchKey] =
    ZIO
      .accessM[Blocking](_.get.effectBlocking(new WatchKey(javaWatchService.take())))
      .refineToOrDie[InterruptedException]
}

object WatchService {
  def forDefaultFileSystem: ZIO[Blocking, IOException, WatchService] = FileSystem.default.newWatchService

  def fromJava(javaWatchService: JWatchService): WatchService = new WatchService(javaWatchService)
}
