package zio.nio.core

import zio.duration._
import zio.test.{ DefaultRunnableSpec, TestAspect }

trait BaseSpec extends DefaultRunnableSpec {
  override def aspects = List(TestAspect.timeout(60.seconds))
}
