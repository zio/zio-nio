package zio.nio.channels

import zio.nio.BaseSpec
import zio.test.Assertion._
import zio.test._
import zio.{Duration, Trace, ZIO}

import java.nio.channels.{AsynchronousChannelGroup => JAsynchronousChannelGroup}
import java.util.concurrent.{ExecutorService => JExecutorService, Executors, TimeUnit}
import scala.concurrent.ExecutionContext

object AsynchronousChannelGroupSpec extends BaseSpec {

  override def spec: Spec[Any, Throwable] =
    suite("AsynchronousChannelGroupSpec")(
      test("awaitTermination") {
        ClassFixture.providedFixture { fixture =>
          fixture.testObj
            .awaitTermination(Duration.apply(1, TimeUnit.SECONDS))
            .map(result => assert(result)(isFalse))
        }
      },
      test("failing awaitTermination") {
        new AsynchronousChannelGroup(null)
          .awaitTermination(Duration.apply(1, TimeUnit.SECONDS))
          .exit
          .map(result => assert(result)(dies(isSubtype[NullPointerException](anything))))
      },
      test("isShutdown") {
        ClassFixture.providedFixture { fixture =>
          fixture.testObj.isShutdown
            .map(result => assert(result)(isFalse))
        }
      },
      test("isTerminated") {
        ClassFixture.providedFixture { fixture =>
          fixture.testObj.isTerminated
            .map(result => assert(result)(isFalse))
        }
      },
      test("shutdown") {
        ClassFixture.providedFixture { fixture =>
          fixture.testObj.shutdown
            .map(_ => assertCompletes)
        }
      },
      test("shutdownNow") {
        ClassFixture.providedFixture { fixture =>
          fixture.testObj.shutdownNow
            .map(_ => assertCompletes)
        }
      },
      test("failing shutdownNow") {
        for {
          channel <- ZIO.attempt(new AsynchronousChannelGroup(null))
          result  <- channel.shutdownNow.exit
        } yield assert(result)(dies(anything))
      },
      test("companion object create instance using executor and initial size") {
        ZIO.acquireReleaseWith {
          ZIO.attempt(ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()))
        } { executor =>
          ZIO.succeed(executor.shutdown())
        } { executor =>
          AsynchronousChannelGroup(executor, 1).exit.map(result => assert(result.toEither)(isRight(anything)))
        }
      },
      test("failing companion object create instance using executor and initial size") {
        for {
          result <- AsynchronousChannelGroup(null, 1).exit
        } yield assert(result)(dies(isSubtype[NullPointerException](anything)))
      },
      test("failing companion object create instance using threads no and threads factory") {
        for {
          result <- AsynchronousChannelGroup(1, null).exit
        } yield assert(result)(dies(isSubtype[NullPointerException](anything)))
      },
      test("companion object create instance using executor service") {
        ZIO.acquireReleaseWith {
          ZIO.attempt(ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()))
        } { executor =>
          ZIO.succeed(executor.shutdown())
        } { executor =>
          AsynchronousChannelGroup(executor).exit.map(result => assert(result.toEither)(isRight(anything)))
        }
      },
      test("failing companion object create instance using executor service") {
        for {
          result <- AsynchronousChannelGroup(null).exit
        } yield assert(result)(dies(isSubtype[NullPointerException](anything)))
      }
    )

  trait ClassFixture {
    def jExecutor: JExecutorService

    def jChannelGroup: JAsynchronousChannelGroup

    def testObj: AsynchronousChannelGroup

    def cleanFixture(): Unit
  }

  object ClassFixture {

    def apply(): ClassFixture =
      new ClassFixture {
        val jExecutor: JExecutorService = Executors.newFixedThreadPool(1)

        val jChannelGroup: JAsynchronousChannelGroup =
          JAsynchronousChannelGroup.withThreadPool(jExecutor)
        val testObj = new AsynchronousChannelGroup(jChannelGroup)

        def cleanFixture(): Unit = {
          jChannelGroup.shutdown()
          jExecutor.shutdown()
        }
      }

    def providedFixture(f: ClassFixture => ZIO[Any, Throwable, TestResult])(implicit
      trace: Trace
    ): ZIO[Any, Throwable, TestResult] =
      ZIO.acquireReleaseWith {
        ZIO.attempt(ClassFixture())
      } { fixture =>
        ZIO.succeed(fixture.cleanFixture())
      } { fixture =>
        f(fixture)
      }
  }
}
