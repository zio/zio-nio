package zio.nio.core

import java.io.IOException

import zio.IO

/**
 * A resource with an effect to close or release the resource.
 */
trait IOCloseable {

  /**
   * Closes this resource.
   */
  def close: IO[IOException, Unit]

}
