package zio.nio.core.channels

import java.nio.channels.{ Channel => JChannel }

import zio.{ IO, UIO }

trait Channel {
  protected val channel: JChannel

  final val close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineToOrDie[Exception]

  /**
   * Tells whether or not this channel is open.
   */
  final val isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)
}
