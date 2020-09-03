package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ Channel => JChannel }

import zio.{ IO, UIO }

trait Channel {

  protected val channel: JChannel

  final private[channels] val close: IO[IOException, Unit] =
    IO.effect(channel.close()).refineToOrDie[IOException]

  /**
   * Tells whether or not this channel is open.
   */
  final def isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)
}
