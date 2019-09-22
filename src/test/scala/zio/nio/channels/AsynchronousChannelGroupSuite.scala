package zio.nio.channels

import java.nio.channels.{ AsynchronousChannelGroup => JAsynchronousChannelGroup }
import java.util.concurrent.{ Executors, TimeUnit, ExecutorService => JExecutorService }

import org.specs2.matcher.MustMatchers
import testz.{ Result, _ }
import zio.DefaultRuntime
import zio.duration.Duration

import scala.concurrent.Future
import scala.concurrent.Future._

object AsynchronousChannelGroupSuite extends DefaultRuntime with MustMatchers {

  trait ClassFixture {
    def jExecutor: JExecutorService
    def jChannelGroup: JAsynchronousChannelGroup
    def testObj: AsynchronousChannelGroup

    def cleanFixture(): Future[Unit]
  }

  object ClassFixture {

    def apply(): Future[ClassFixture] = successful {
      new ClassFixture {
        val jExecutor: JExecutorService = Executors.newFixedThreadPool(1)
        val jChannelGroup: JAsynchronousChannelGroup =
          JAsynchronousChannelGroup.withThreadPool(jExecutor)
        val testObj = new AsynchronousChannelGroup(jChannelGroup)

        def cleanFixture(): Future[Unit] = successful {
          jChannelGroup.shutdown()
          jExecutor.shutdown()
        }
      }
    }
  }

  implicit def assertAsFuture(r: Result): Future[Result] =
    Future.successful(r)

  def tests[T[_]](harness: EffectResourceHarness[Future, T]): T[Unit] = {
    import harness._

    def noResBracket[R](tests: T[(Unit, R)]): T[R] =
      bracket(() => successful(()))(_ => successful(()))(tests)

    section(
      namedSection("awaitTermination")(
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("successfully") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              val result = unsafeRun(
                testObj.awaitTermination(Duration.apply(1, TimeUnit.SECONDS))
              )

              //TODO group is returning always false as a termination status, do we want to have true?
              assert(!result)
          }
        },
        noResBracket(
          test("fails") {
            case (_, _) =>
              assert(
                unsafeRunSync(
                  new AsynchronousChannelGroup(null)
                    .awaitTermination(Duration.apply(1, TimeUnit.SECONDS))
                ).toEither must beLeft
              )
          }
        )
      ),
      namedSection("isShutdown")(
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("returns false") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              val result = unsafeRun(testObj.isShutdown)
              assert(!result)
          }
        }
      ),
      namedSection("isTerminated")(
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("returns false") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              val result = unsafeRun(testObj.isTerminated)
              assert(!result)
          }
        }
      ),
      namedSection("shutdown")(
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("successfully") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              unsafeRun(testObj.shutdown)

              assert(true)
          }
        }
      ),
      namedSection("shutdownNow")(
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("successfully") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              assert(unsafeRunSync(testObj.shutdownNow).toEither must beRight)
          }
        },
        noResBracket(
          test("fails") {
            case (_, _) =>
              assert(
                unsafeRunSync(new AsynchronousChannelGroup(null).shutdownNow).toEither must beLeft
              )
          }
        )
      ),
      namedSection("companion object create instance")(
        noResBracket(
          test("successfully") {
            case (_, _) =>
              assert(unsafeRunSync(AsynchronousChannelGroup.apply).toEither must beRight)
          }
        )
      )
    )
  }
}
