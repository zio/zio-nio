package zio.nio

import zio.IO

import java.io.IOException

/**
 * A resource with an effect to close or release the resource.
 */
trait IOCloseable {

  /**
   * Closes this resource.
   */
  def close: IO[IOException, Unit]

}
