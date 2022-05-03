package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Trace}

import java.io.IOException

/**
 * A resource with an effect to close or release the resource.
 */
trait IOCloseable {

  /**
   * Closes this resource.
   */
  def close(implicit trace: Trace): IO[IOException, Unit]

}
