package zio.nio.channels

import java.nio.channels.{ AsynchronousChannelGroup => JAsynchronousChannelGroup }
import java.util.concurrent.{ Executors, TimeUnit, ExecutorService => JExecutorService }

import zio.ZIO
import zio.duration.Duration
import zio.nio.BaseSpec
import zio.test._
import zio.test.Assertion._

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

  def providedFixture(f: ClassFixture => ZIO[Any, Throwable, TestResult]): ZIO[Any, Throwable, TestResult] =
    ZIO(ClassFixture()).bracket(fixture => ZIO.effectTotal(fixture.cleanFixture())) { fixture =>
      f(fixture)
    }
}

object AsynchronousChannelGroupSpec
    extends BaseSpec(
      suite("AsynchronousChannelGroupSpec")(
        testM("awaitTermination") {
          ClassFixture.providedFixture { fixture =>
            fixture.testObj
              .awaitTermination(Duration.apply(1, TimeUnit.SECONDS))
              .map(result => assert(result, isFalse))
          }
        },
        testM("failing awaitTermination") {
          new AsynchronousChannelGroup(null)
            .awaitTermination(Duration.apply(1, TimeUnit.SECONDS))
            .run
            .map(result => assert(result, fails(anything)))
        },
        testM("isShutdown") {
          ClassFixture.providedFixture { fixture =>
            fixture.testObj.isShutdown
              .map(result => assert(result, isFalse))
          }
        },
        testM("isTerminated") {
          ClassFixture.providedFixture { fixture =>
            fixture.testObj.isTerminated
              .map(result => assert(result, isFalse))
          }
        },
        testM("shutdown") {
          ClassFixture.providedFixture { fixture =>
            fixture.testObj.shutdown
              .map(_ => assert(true, isTrue))
          }
        },
        testM("shutdownNow") {
          ClassFixture.providedFixture { fixture =>
            fixture.testObj.shutdownNow
              .map(_ => assert(true, isTrue))
          }
        },
        testM("failing shutdownNow") {
          for {
            channel <- ZIO.effect(new AsynchronousChannelGroup(null))
            result  <- channel.shutdownNow.run
          } yield assert(result, dies(anything))
        },
        testM("companion object create instance") {
          AsynchronousChannelGroup.apply.run.map(result => assert(result.toEither, isRight(anything)))
        }
      )
    )
