package zio.nio.channels

import zio.{ IO, UIO }

import java.io.IOException
import java.nio.channels.{ Channel => JChannel }

trait Channel {

  protected val channel: JChannel

  final private[channels] val close: IO[IOException, Unit] =
    IO.effect(channel.close()).refineToOrDie[IOException]

  /**
   * Tells whether or not this channel is open.
   */
  final def isOpen: UIO[Boolean] = IO.effectTotal(channel.isOpen)
}
