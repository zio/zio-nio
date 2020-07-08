package zio.nio.core

import java.io.IOException

import zio.ZIO

/**
 * A resource with an effect to close or release the resource.
 *
 * @tparam R The environment required by the close effect.
 */
trait IOCloseable[-R] {

  /**
   * Closes this resource.
   */
  def close: ZIO[R, IOException, Unit]

}
