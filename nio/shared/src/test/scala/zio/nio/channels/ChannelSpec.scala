package zio.nio.channels

import zio._
import zio.nio.{BaseSpec, EffectOps}
import zio.test.Assertion._
import zio.test._

import java.io.{EOFException, FileNotFoundException, IOException}

object ChannelSpec extends BaseSpec {

  override def spec =
    suite("Channel")(
      suite("explicit end-of-stream")(
        test("converts EOFException to None") {
          assertZIO(ZIO.fail(new EOFException).eofCheck.exit)(fails(isNone))
        },
        test("converts non EOFException to Some") {
          val e: IOException = new FileNotFoundException()
          assertZIO(ZIO.fail(e).eofCheck.exit)(fails(isSome(equalTo(e))))
        },
        test("passes through success") {
          assertZIO(ZIO.succeed(42).eofCheck.exit)(succeeds(equalTo(42)))
        }
      )
    )
}
