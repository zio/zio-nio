package zio.nio.core.file

import zio.nio.core.BaseSpec
import zio.test.Assertion._
import zio.test._

object PathSpec
    extends BaseSpec(
      suite("PathSpec")(
        test("Path construction") {
          val p        = Path("a", "b") / "c/d"
          val elements = p.elements.map(_.toString)
          assert(elements)(equalTo(List("a", "b", "c", "d")))
        }
      )
    )
