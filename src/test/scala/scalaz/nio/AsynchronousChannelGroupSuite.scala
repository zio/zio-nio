package scalaz.nio
import scalaz.zio.RTS
import testz.{Harness, ResourceHarness, assert}
import java.nio.channels.{AsynchronousChannelGroup => JAsynchronousChannelGroup}

import org.scalamock.scalatest.MockFactory
import scalaz.nio.channels.AsynchronousChannelGroup

object AsynchronousChannelGroupSuite extends RTS with MockFactory{
  class Fixture {
    val jGroupMock = mock[JAsynchronousChannelGroup]
    val testObj = new AsynchronousChannelGroup(jGroupMock)
  }

  def tests[T](harness: ResourceHarness[T]): T = {
    import harness._
    section(
      namedSection("awaitTermination") {
        allocate(() => List(1))(test("fails") { case (list, _) =>

          assert(1 == 1)
        })
        test("succeeds") { () =>

        }
      }
    )
  }
}
