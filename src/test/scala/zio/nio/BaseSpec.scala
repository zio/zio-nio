package zio.nio

import zio.duration._
import zio.test.{ DefaultRunnableSpec, TestAspect, ZSpec }
import zio.test.environment.TestEnvironment

abstract class BaseSpec(spec: => ZSpec[TestEnvironment, Throwable, String, Any])
    extends DefaultRunnableSpec(spec, List(TestAspect.timeout(60.seconds)))
