package scalaz.nio

import testz._
import runner.TestOutput

import scala.concurrent.{ Await, ExecutionContext, Future }
import ExecutionContext.global
import scala.concurrent.duration.Duration
import scala.concurrent.duration._

object Main {

  def main(args: Array[String]): Unit = {
    val printer: (Result, List[String]) => Unit =
      (tr, ls) => runner.printStrs(runner.printTest(tr, ls), Console.print)

    val ec = global

    val pureHarness   = PureHarness.makeFromPrinter(printer)
    val effectHarness = FutureHarness.makeFromPrinterEffR(printer)(ec)

    def unitTests =
      TestOutput.combineAll1(
        BufferSuite.tests(pureHarness)((), List("Buffer tests")),
        ChannelSuite.tests(pureHarness)((), List("Channel tests")),
        FileChannelSuite.tests(pureHarness)((), List("FileChannel tests")),
        ScatterGatherChannelSuite
          .tests(pureHarness)((), List("Scattering and Gathering Channel tests"))
      )

    def asyncChannelGroupSuite =
      AsynchronousChannelGroupSuite
        .tests(effectHarness)((), List("Asynchronous Channel Group tests"))

    // Evaluate tests before the runner expects,
    // for parallelism.
    val testOutputs: List[() => Future[TestOutput]] = List(
      Future(unitTests)(ec),
      asyncChannelGroupSuite
    ).map(s => () => s)

    val runSuites = runner(testOutputs, Console.print, global)
    val result    = Await.result(runSuites, Duration.Inf)

    if (result.failed) throw new Exception("some tests failed")
  }
}
