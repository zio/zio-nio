package zio.nio

import zio._
import zio.test.{Live, TestAspect, TestAspectAtLeastR, ZIOSpecDefault}

trait BaseSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspectAtLeastR[Live]] = Chunk(TestAspect.timeout(60.seconds))
}
