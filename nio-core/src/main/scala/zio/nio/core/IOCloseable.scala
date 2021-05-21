package zio.nio.core

import zio.ZIO

import java.io.IOException

/**
 * A resource with an effect to close or release the resource.
 */
trait IOCloseable {

  type Env

  /**
   * Closes this resource.
   */
  def close: ZIO[Env, IOException, Unit]

}
