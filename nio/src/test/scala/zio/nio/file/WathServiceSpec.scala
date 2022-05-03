package zio.nio.file

import zio.Scope
import zio.nio.BaseSpec
import zio.test.Assertion._
import zio.test._

import java.io.IOException
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE

object WathServiceSpec extends BaseSpec {

  override def spec: Spec[Scope, IOException] =
    suite("WatchServiceSpec")(
      test("Watch Service register")(
        FileSystem.default.newWatchService.flatMap { watchService =>
          for {
            watchKey <- Path("nio/src/test/resources").register(watchService, ENTRY_CREATE)
            watchable = watchKey.watchable
          } yield assert(watchable)(equalTo(Path("nio/src/test/resources")))
        }
      )
    )
}
