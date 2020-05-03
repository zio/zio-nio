package zio.nio.core.file

import zio.nio.core.BaseSpec
import zio.test.Assertion._
import zio.test._
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE

object WathServiceSpec extends BaseSpec {

  override def spec = suite("WatchServiceSpec")(
    testM("Watch Service register")(
      for {
        watchService <- FileSystem.default.newWatchService
        watchKey     <- Path("nio-core/src/test/resources").register(watchService, ENTRY_CREATE)
        watchable    <- watchKey.watchable
      } yield assert(watchable)(equalTo(Path("nio-core/src/test/resources")))
    )
  )
}
