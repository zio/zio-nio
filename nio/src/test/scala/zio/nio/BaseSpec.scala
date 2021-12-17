package zio.nio

import zio._
import zio.test.Live
import zio.test.{DefaultRunnableSpec, TestAspect, TestAspectAtLeastR}

trait BaseSpec extends DefaultRunnableSpec {
  override def aspects: List[TestAspectAtLeastR[Live]] = List(TestAspect.timeout(60.seconds))
}
