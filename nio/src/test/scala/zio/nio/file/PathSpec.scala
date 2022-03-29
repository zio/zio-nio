package zio.nio.file

import zio.nio.BaseSpec
import zio.test.Assertion._
import zio.test._

object PathSpec extends BaseSpec {

  override def spec =
    suite("PathSpec")(
      test("Path construction") {
        val p        = Path("a", "b") / "c/d"
        val elements = p.elements.map(_.toString)
        assert(elements)(equalTo(List("a", "b", "c", "d")))
      },
      test("Path equality") {
        val path1 = Path("a") / "b" / "c"
        val path2 = Path("a", "b") / "c"
        assert(path1)(equalTo(path2))
      }
    )
}
