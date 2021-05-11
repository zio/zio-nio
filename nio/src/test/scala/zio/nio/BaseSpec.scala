package zio.nio

import zio.duration._
import zio.test.environment.Live
import zio.test.{ DefaultRunnableSpec, TestAspect, TestAspectAtLeastR }

trait BaseSpec extends DefaultRunnableSpec {
  override def aspects: List[TestAspectAtLeastR[Live]] = List(TestAspect.timeout(60.seconds))
}
