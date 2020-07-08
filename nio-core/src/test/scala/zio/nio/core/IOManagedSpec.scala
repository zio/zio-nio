package zio.nio.core

import java.io.IOException

import zio.{ IO, Ref }
import zio.test.{ suite, testM, _ }
import zio.test.Assertion._

object IOManagedSpec extends BaseSpec {

  final private class TestResource(ref: Ref[Boolean]) extends IOCloseable[Any] {

    override def close: IO[IOException, Unit] = ref.set(true)

  }

  override def spec =
    suite("IOManagedSpec")(
      testM("ZManaged value calls close") {
        for {
          ref    <- Ref.make(false)
          _      <- IO.succeed(new TestResource(ref)).toManagedNio.use(_ => IO.unit)
          result <- ref.get
        } yield assert(result)(isTrue)
      },
      testM("bracket calls close") {
        for {
          ref    <- Ref.make(false)
          _      <- IO.succeed(new TestResource(ref)).bracketNio(_ => IO.unit)
          result <- ref.get
        } yield assert(result)(isTrue)
      }
    )

}
