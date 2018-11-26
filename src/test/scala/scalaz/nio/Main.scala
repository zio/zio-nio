package scalaz.nio

import testz._
import runner.TestOutput

import scala.concurrent.{ Await, ExecutionContext, Future }
import ExecutionContext.global
import scala.concurrent.duration.Duration

object Main {

  def main(args: Array[String]): Unit = {
    val printer: (Result, List[String]) => Unit =
      (tr, ls) => runner.printStrs(runner.printTest(tr, ls), Console.print)

    val ec = global

    val pureHarness = PureHarness.makeFromPrinter(printer)

    def unitTests =
      TestOutput.combineAll1(
        BufferSuite.tests(pureHarness)((), List("Buffer tests")),
        ByteBufferSuite.tests(pureHarness)((), List("ByteBuffer tests")),
        ChannelSuite.tests(pureHarness)((), List("Channel tests"))
      )

    // Evaluate tests before the runner expects,
    // for parallelism.
    val testOutputs: List[() => Future[TestOutput]] = List(
      Future(unitTests)(ec)
    ).map(s => () => s)

    val runSuites = runner(testOutputs, Console.print, global)
    val result    = Await.result(runSuites, Duration.Inf)

    if (result.failed) throw new Exception("some tests failed")
  }
}
