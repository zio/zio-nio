package zio.nio

import zio.duration._
import zio.test.{ DefaultRunnableSpec, TestAspect, ZSpec }
import zio.test.mock.MockEnvironment

abstract class BaseSpec(spec: => ZSpec[MockEnvironment, Throwable, String, Any])
    extends DefaultRunnableSpec(spec, List(TestAspect.timeout(60.seconds)))
