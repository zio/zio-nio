package zio.nio.core.channels

import zio.nio.core.IOCloseable
import zio.{ IO, UIO }

import java.io.IOException
import java.nio.channels.{ Channel => JChannel }

trait Channel extends IOCloseable {

  type Env = Any

  protected val channel: JChannel

  /**
   * Closes this channel.
   */
  final def close: IO[IOException, Unit] = IO.effect(channel.close()).refineToOrDie[IOException]

  /**
   * Tells whether or not this channel is open.
   */
  final def isOpen: UIO[Boolean] = IO.effectTotal(channel.isOpen)
}
