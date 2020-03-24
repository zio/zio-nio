package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ ClosedSelectorException, Selector => JSelector, SelectionKey => JSelectionKey }

import zio.{ IO, UIO }
import com.github.ghik.silencer.silent
import zio.duration.Duration
import zio.nio.core.channels.spi.SelectorProvider
import zio.{ IO, UIO }

import scala.jdk.CollectionConverters._

class Selector(private[nio] val selector: JSelector) {
  final val isOpen: UIO[Boolean] = IO.effectTotal(selector.isOpen)

  final val provider: UIO[SelectorProvider] =
    IO.effectTotal(selector.provider()).map(new SelectorProvider(_))

  @silent
  final val keys: IO[ClosedSelectorException, Set[SelectionKey]] =
    IO.effect(selector.keys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  @silent
  final val selectedKeys: IO[ClosedSelectorException, Set[SelectionKey]] =
    IO.effect(selector.selectedKeys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  final def removeKey(key: SelectionKey): IO[ClosedSelectorException, Unit] =
    IO.effect(selector.selectedKeys().remove(key.selectionKey))
      .unit
      .refineToOrDie[ClosedSelectorException]

  /**
   * Can throw IOException and ClosedSelectorException.
   */
  final val selectNow: IO[Exception, Int] =
    IO.effect(selector.selectNow()).refineToOrDie[Exception]

  /**
   * Can throw IOException and ClosedSelectorException.
   */
  final def select(timeout: Duration): IO[Exception, Int] =
    IO.effect(selector.select(timeout.toMillis)).refineToOrDie[Exception]

  /**
   * Can throw IOException and ClosedSelectorException.
   */
  final val select: IO[Exception, Int] =
    IO.effect(selector.select()).refineToOrDie[IOException]

  final val wakeup: IO[Nothing, Selector] =
    IO.effectTotal(selector.wakeup()).map(new Selector(_))

  final val close: IO[IOException, Unit] =
    IO.effect(selector.close()).refineToOrDie[IOException].unit
}

object Selector {

  final val make: IO[IOException, Selector] =
    IO.effect(new Selector(JSelector.open())).refineToOrDie[IOException]
}
