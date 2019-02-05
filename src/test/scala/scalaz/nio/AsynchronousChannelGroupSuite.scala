package scalaz.nio
import java.nio.channels.{ AsynchronousChannelGroup => JAsynchronousChannelGroup }
import java.util.concurrent.TimeUnit

import org.specs2.matcher.MustMatchers
import scalaz.nio.channels.AsynchronousChannelGroup
import scalaz.zio.RTS
import scalaz.zio.duration.Duration
import testz.{ Result, _ }

import scala.concurrent.Future._
import java.util.concurrent.{ Executors, ExecutorService => JExecutorService }

import scala.concurrent.Future

object AsynchronousChannelGroupSuite extends RTS with MustMatchers {

  trait ClassFixture {
    def jExecutor: JExecutorService
    def jChannelGroup: JAsynchronousChannelGroup
    def testObj: AsynchronousChannelGroup

    def cleanFixture(): Future[Unit]
  }

  object ClassFixture {

    def apply(): Future[ClassFixture] = successful {
      new ClassFixture {
        val jExecutor = Executors.newFixedThreadPool(1)
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
      namedSection("isShutdown") {
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("returns false") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              assert(!testObj.isShutdown)
          }
        }
      },
      namedSection("isTerminated") {
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("returns false") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              assert(!testObj.isTerminated)
          }
        }
      },
      namedSection("shutdown") {
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("successfully") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              testObj.shutdown()

              assert(true)
          }
        }
      },
      namedSection("shutdownNow")(
        bracket(() => ClassFixture()) { _.cleanFixture() } {
          test("successfully") {
            case (fixture: ClassFixture, _) =>
              import fixture.testObj

              assert(unsafeRunSync(testObj.shutdownNow()).toEither must beRight)
          }
        },
        noResBracket(
          test("fails") {
            case (_, _) =>
              assert(
                unsafeRunSync(new AsynchronousChannelGroup(null).shutdownNow()).toEither must beLeft
              )
          }
        )
      ),
      namedSection("companion object create instance using executor and initial size")(
        bracket(() => successful(Executors.newCachedThreadPool())) { executor =>
          successful(executor.shutdown())
        } {
          test("successfully") {
            case (executor: JExecutorService, _) =>
              assert(unsafeRunSync(AsynchronousChannelGroup(executor, 1)).toEither must beRight)
          }
        },
        noResBracket(
          test("fails") {
            case (_, _) =>
              assert(unsafeRunSync(AsynchronousChannelGroup(null, 1)).toEither must beLeft)
          }
        )
      ),
      namedSection("companion object create instance using threads no and threads factory")(
        bracket(() => successful(())) { _ =>
          successful(())
        } {
          test("successfully") {
            case (_, _) =>
              assert(
                unsafeRunSync(AsynchronousChannelGroup(1, Executors.defaultThreadFactory())).toEither must beRight
              )

          }
        },
        noResBracket(
          test("fails") {
            case (_, _) =>
              assert(unsafeRunSync(AsynchronousChannelGroup(1, null)).toEither must beLeft)
          }
        )
      ),
      namedSection("companion object create instance using executor service")(
        bracket(() => successful(Executors.newCachedThreadPool())) { executor =>
          successful(executor.shutdown())
        } {
          test("successfully") {
            case (executor: JExecutorService, _) =>
              assert(unsafeRunSync(AsynchronousChannelGroup(executor)).toEither must beRight)
          }
        },
        noResBracket(
          test("fails") {
            case (_, _) =>
              assert(unsafeRunSync(AsynchronousChannelGroup(null)).toEither must beLeft)
          }
        )
      )
    )
  }
}
