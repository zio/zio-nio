package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ ClosedSelectorException, Selector => JSelector, SelectionKey => JSelectionKey }

import zio.{ IO, Managed, UIO, ZIO }
import com.github.ghik.silencer.silent
import zio.duration.Duration
import zio.nio.channels.spi.SelectorProvider
import zio.nio.core.channels.SelectionKey
import zio.blocking
import zio.blocking.Blocking

import scala.jdk.CollectionConverters._

class Selector(private[nio] val selector: JSelector) {

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
  final def select(timeout: Duration): ZIO[Blocking, Exception, Int] =
    blocking
      .effectBlockingCancelable(selector.select(timeout.toMillis))(wakeup)
      .refineToOrDie[Exception]

  /**
   * Can throw IOException and ClosedSelectorException.
   */
  final def select: ZIO[Blocking, Exception, Int] =
    blocking
      .effectBlockingCancelable(selector.select())(wakeup)
      .refineToOrDie[IOException]

  final val wakeup: IO[Nothing, Unit] =
    IO.effectTotal(selector.wakeup()).unit

  final private[channels] val close: IO[IOException, Unit] =
    IO.effect(selector.close()).refineToOrDie[IOException].unit
}

object Selector {

  final val make: Managed[IOException, Selector] = {
    val open = IO.effect(new Selector(JSelector.open())).refineToOrDie[IOException]
    Managed.make(open)(_.close.orDie)
  }
}
