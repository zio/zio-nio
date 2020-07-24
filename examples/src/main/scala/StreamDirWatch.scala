package zio
package nio
package examples

import java.nio.file.{ StandardWatchEventKinds, WatchEvent }

import zio.nio.core._
import zio.nio.core.file.{ Path, WatchService }

/**
 * Example of using the `ZStream` API for watching a file system directory for events.
 *
 * Note that on macOS the standard Java `WatchService` uses polling and so is a bit slow,
 * and only registers at most one type of event for each directory member since the last poll.
 */
object StreamDirWatch extends App {

  private def watch(dir: Path) =
    WatchService.forDefaultFileSystem.bracketNio { service =>
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
        _ <- console.putStrLn(s"Watching directory '$dir'")
        _ <- console.putStrLn("")
        _ <- service.stream.foreach { key =>
               val eventProcess = { (event: WatchEvent[_]) =>
                 val desc = event.kind() match {
                   case StandardWatchEventKinds.ENTRY_CREATE => "Create"
                   case StandardWatchEventKinds.ENTRY_MODIFY => "Modify"
                   case StandardWatchEventKinds.ENTRY_DELETE => "Delete"
                   case StandardWatchEventKinds.OVERFLOW     => "** Overflow **"
                   case other                                => s"Unknown: $other"
                 }
                 val path = key.resolveEventPath(event).getOrElse(" ** PATH UNKNOWN **")
                 console.putStrLn(s"$desc, count: ${event.count()}, $path")
               }
               key.pollEventsManaged.use(ZIO.foreach(_)(eventProcess))
             }
      } yield ()
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    args.headOption
      .map(dirString => watch(Path(dirString)).exitCode)
      .getOrElse(console.putStrLn("A directory argument is required").as(ExitCode.failure))

}
