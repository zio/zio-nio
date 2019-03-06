package scalaz.nio.channels

import java.io.IOException
import java.nio.channels.{ Selector => JSelector }

import scalaz.nio.channels.spi.SelectorProvider
import scalaz.nio.io._
import scalaz.zio.{ IO, UIO }
import scalaz.zio.duration.Duration

import scala.collection.JavaConverters

class Selector(private[nio] val selector: JSelector) {

  final val isOpen: UIO[Boolean] = IO.effectTotal(selector.isOpen)

  final val provider: UIO[SelectorProvider] =
    IO.effectTotal(selector.provider()).map(new SelectorProvider(_))

  final val keys: UIO[Set[SelectionKey]] =
    IO.effectTotal(selector.keys()).map { keys =>
      JavaConverters.asScalaSet(keys).toSet.map(new SelectionKey(_))
    }

  final val selectedKeys: UIO[Set[SelectionKey]] =
    IO.effectTotal(selector.selectedKeys()).map { keys =>
      JavaConverters.asScalaSet(keys).toSet.map(new SelectionKey(_))
    }

  final def removeKey(key: SelectionKey): UIO[Unit] =
    IO.effectTotal(selector.selectedKeys().remove(key.selectionKey)).void

  final val selectNow: IO[IOException, Int] =
    IO.effect(selector.selectNow()).refineOrDie(JustIOException)

  final def select(timeout: Duration): IO[IOException, Int] =
    IO.effect(selector.select(timeout.toMillis)).refineOrDie(JustIOException)

  final val select: IO[IOException, Int] =
    IO.effect(selector.select()).refineOrDie(JustIOException)

  final val wakeup: IO[Nothing, Selector] =
    IO.effectTotal(selector.wakeup()).map(new Selector(_))

  final val close: IO[IOException, Unit] =
    IO.effect(selector.close()).refineOrDie(JustIOException).void
}

object Selector {

  final val make: IO[IOException, Selector] =
    IO.effect(new Selector(JSelector.open())).refineOrDie(JustIOException)

}
