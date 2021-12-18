package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, ZTraceElement}

import java.io.IOException

/**
 * A resource with an effect to close or release the resource.
 */
trait IOCloseable {

  /**
   * Closes this resource.
   */
  def close(implicit trace: ZTraceElement): IO[IOException, Unit]

}
