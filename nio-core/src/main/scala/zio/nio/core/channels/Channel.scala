package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ Channel => JChannel }

import zio.{ IO, UIO }
import zio.nio.core.IOCloseable

trait Channel extends IOCloseable[Any] {

  protected val channel: JChannel

  /**
   * Closes this channel.
   */
  final override def close: IO[IOException, Unit] =
    IO.effect(channel.close()).refineToOrDie[IOException]

  /**
   * Tells whether or not this channel is open.
   */
  final def isOpen: UIO[Boolean] =
    IO.effectTotal(channel.isOpen)
}
