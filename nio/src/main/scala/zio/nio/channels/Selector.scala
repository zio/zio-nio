package zio.nio.channels

import com.github.ghik.silencer.silent
import zio.blocking.Blocking
import zio.duration.Duration
import zio.nio.channels.spi.SelectorProvider
import zio.nio.core.channels.SelectionKey
import zio.{ IO, Managed, UIO, ZIO, blocking }

import java.io.IOException
import java.nio.channels.{ ClosedSelectorException, SelectionKey => JSelectionKey, Selector => JSelector }
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class Selector(private[nio] val selector: JSelector) {

  final val provider: UIO[SelectorProvider] =
    IO.effectTotal(selector.provider()).map(new SelectorProvider(_))

  @silent
  final val keys: IO[ClosedSelectorException, Set[SelectionKey]] =
    IO.effect(selector.keys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  /**
   * Returns this selector's selected-key set.
   *
   * Note that the returned set it mutable - keys may be removed from, but not directly added to it.
   * Any attempt to add an object to the key set will cause an `UnsupportedOperationException` to be thrown.
   * The selected-key set is not thread-safe.
   */
  @silent
  final val selectedKeys: IO[ClosedSelectorException, mutable.Set[SelectionKey]] =
    IO.effect(selector.selectedKeys())
      .map(_.asScala.map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  /**
   * Performs an effect with each selected key.
   *
   * If the result of effect is true, the key will be removed from the selected-key set, which is
   * usually what you want after successfully handling a selected key.
   */
  def foreachSelectedKey[R, E](f: SelectionKey => ZIO[R, E, Boolean]): ZIO[R, E, Unit] =
    ZIO.effectTotal(selector.selectedKeys().iterator()).flatMap { iter =>
      def loop: ZIO[R, E, Unit] =
        ZIO.effectSuspendTotal {
          if (iter.hasNext) {
            val key = iter.next()
            f(new SelectionKey(key)).flatMap(ZIO.when(_)(ZIO.effectTotal(iter.remove()))) *>
              loop
          } else
            ZIO.unit
        }

      loop
    }

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
