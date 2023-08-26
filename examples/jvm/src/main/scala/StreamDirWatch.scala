package zio
package nio
package examples

import zio.nio.file.Path
import zio.nio.file.WatchService

import zio.{Console, ZIOAppDefault}

import java.nio.file.{StandardWatchEventKinds, WatchEvent}

/**
 * Example of using the `ZStream` API for watching a file system directory for events.
 *
 * Note that on macOS the standard Java `WatchService` uses polling and so is a bit slow, and only registers at most one
 * type of event for each directory member since the last poll.
 */
object StreamDirWatch extends ZIOAppDefault {

  private def watch(dir: Path) =
    ZIO.scoped {
      WatchService.forDefaultFileSystem.flatMap { service =>
        for {
          _ <- dir.registerTree(
                 watcher = service,
                 events = Set(
                   StandardWatchEventKinds.ENTRY_CREATE,
                   StandardWatchEventKinds.ENTRY_MODIFY,
                   StandardWatchEventKinds.ENTRY_DELETE
                 ),
                 maxDepth = 100
               )
          _ <- Console.printLine(s"Watching directory '$dir'")
          _ <- Console.printLine("")
          _ <- service.stream.foreach { key =>
                 val eventProcess = { (event: WatchEvent[_]) =>
                   val desc = event.kind() match {
                     case StandardWatchEventKinds.ENTRY_CREATE => "Create"
                     case StandardWatchEventKinds.ENTRY_MODIFY => "Modify"
                     case StandardWatchEventKinds.ENTRY_DELETE => "Delete"
                     case StandardWatchEventKinds.OVERFLOW     => "** Overflow **"
                     case other                                => s"Unknown: $other"
                   }
                   val path = key.resolveEventPath(event).getOrElse("** PATH UNKNOWN **")
                   Console.printLine(s"$desc, count: ${event.count()}, $path")
                 }
                 ZIO.scoped(key.pollEventsScoped.flatMap(ZIO.foreachDiscard(_)(eventProcess)))
               }
        } yield ()
      }
    }

  override def run: URIO[ZIOAppArgs, ExitCode] =
    ZIO
      .serviceWith[ZIOAppArgs](_.getArgs.toList.headOption)
      .flatMap(
        _.map(dirString => watch(Path(dirString)).exitCode)
          .getOrElse(Console.printLine("A directory argument is required").exitCode)
      )

}
