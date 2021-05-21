package zio.nio.core.file

import zio.blocking.Blocking
import zio.nio.core.BaseSpec
import zio.test.Assertion._
import zio.test._

import java.io.IOException
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE

object WathServiceSpec extends BaseSpec {

  override def spec: Spec[Blocking, TestFailure[IOException], TestSuccess] =
    suite("WatchServiceSpec")(
      testM("Watch Service register")(
        FileSystem.default.newWatchService.use { watchService =>
          for {
            watchKey  <- Path("nio-core/src/test/resources").register(watchService, ENTRY_CREATE)
            watchable <- watchKey.watchable
          } yield assert(watchable)(equalTo(Path("nio-core/src/test/resources")))
        }
      )
    )
}
