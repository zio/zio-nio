package zio.nio
package channels

import java.io.IOException
import java.nio.channels.{ SelectionKey => JSelectionKey, Selector => JSelector }

import com.github.ghik.silencer.silent
import zio.duration.Duration
import zio.nio.channels.spi.SelectorProvider
import zio.{ IO, Managed, UIO, ZIO }

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * A multiplexor of `SelectableChannel` objects.
 *
 * Please thoroughly read
 * [[https://docs.oracle.com/javase/8/docs/api/java/nio/channels/Selector.html the documentation for the underlying Java]]
 * API before attempting to use this.
 */
final class Selector(private[nio] val selector: JSelector) extends IOCloseable {

  type Env = Any

  val isOpen: UIO[Boolean] = IO.effectTotal(selector.isOpen)

  val provider: UIO[SelectorProvider] =
    IO.effectTotal(selector.provider()).map(new SelectorProvider(_))

  @silent
  val keys: UIO[Set[SelectionKey]] =
    IO.effectTotal(selector.keys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))

  /**
   * Returns this selector's selected-key set.
   *
   * Note that the returned set it mutable - keys may be removed from, but not directly added to it.
   * Any attempt to add an object to the key set will cause an `UnsupportedOperationException` to be thrown.
   * The selected-key set is not thread-safe.
   */
  @silent
  val selectedKeys: UIO[mutable.Set[SelectionKey]] =
    IO.effectTotal(selector.selectedKeys())
      .map(_.asScala.map(new SelectionKey(_)))

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

  def removeKey(key: SelectionKey): UIO[Unit] = IO.effectTotal(selector.selectedKeys().remove(key.selectionKey)).unit

  /**
   * Selects a set of keys whose corresponding channels are ready for I/O operations.
   * This method performs a non-blocking selection operation.
   * If no channels have become selectable since the previous selection
   * operation then this method immediately returns zero.
   *
   * @return The number of keys, possibly zero, whose ready-operation sets
   *         were updated by the selection operation.
   */
  val selectNow: IO[IOException, Int] =
    IO.effect(selector.selectNow()).refineToOrDie[IOException]

  /**
   * Performs a blocking select operation.
   *
   * **Note this will very often block**.
   * This is intended to be used when the effect is locked to an Executor
   * that is appropriate for this.
   * If the fiber is interrupted while blocked in `select`, then `wakeup` is used to unblock it.
   *
   * Dies with `ClosedSelectorException` if this selector is closed.
   *
   * @return The number of keys, possibly zero, whose ready-operation sets were updated
   */
  def select(timeout: Duration): IO[IOException, Int] =
    IO.effect(selector.select(timeout.toMillis)).refineToOrDie[IOException].fork.flatMap(_.join).onInterrupt(wakeup)

  /**
   * Performs a blocking select operation.
   *
   * **Note this will very often block**.
   * This is intended to be used when the effect is locked to an Executor
   * that is appropriate for this.
   * If the fiber is interrupted while blocked in `select`, then `wakeup` is used to unblock it.
   *
   * Dies with `ClosedSelectorException` if this selector is closed.
   *
   * @return The number of keys, possibly zero, whose ready-operation sets were updated
   */
  def select: IO[IOException, Int] =
    IO.effect(selector.select()).refineToOrDie[IOException].fork.flatMap(_.join).onInterrupt(wakeup)

  /**
   * Causes the first selection operation that has not yet returned to return immediately.
   *
   * If another thread is currently blocked in an invocation of the
   * `select()` or `select(long)` methods then that invocation will return
   * immediately. If no selection operation is currently in progress then the
   * next invocation of one of these methods will return immediately unless the
   * `selectNow()` method is invoked in the meantime.
   * In any case the value returned by that invocation may be non-zero.
   * Subsequent invocations of the `select()` or `select(long)` methods will
   * block as usual unless this method is invoked again in the meantime.
   * Invoking this method more than once between two successive selection
   * operations has the same effect as invoking it just once.
   */
  def wakeup: IO[Nothing, Unit] = IO.effectTotal(selector.wakeup()).unit

  /**
   * Closes this selector.
   *
   * If a thread is currently blocked in one of this selector's selection methods
   * then it is interrupted as if by invoking the selector's wakeup method.
   * Any uncancelled keys still associated with this selector are invalidated,
   * their channels are deregistered, and any other resources associated with
   * this selector are released.
   * If this selector is already closed then invoking this method has no effect.
   * After a selector is closed, any further attempt to use it, except by
   * invoking this method or the wakeup method, will cause a
   * `ClosedSelectorException` to be raised as a defect.
   */
  def close: IO[IOException, Unit] = IO.effect(selector.close()).refineToOrDie[IOException]

}

object Selector {

  /**
   * Opens a selector.
   */
  val open: Managed[IOException, Selector] =
    IO.effect(new Selector(JSelector.open())).refineToOrDie[IOException].toNioManaged

}
