package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ SelectionKey => JSelectionKey, Selector => JSelector }

import com.github.ghik.silencer.silent
import zio.duration.Duration
import zio.nio.core.channels.spi.SelectorProvider
import zio.{ IO, UIO }
import zio.nio.core.IOCloseable

import scala.jdk.CollectionConverters._

/**
 * A multiplexor of `SelectableChannel` objects.
 *
 * Please thoroughly read
 * [[https://docs.oracle.com/javase/8/docs/api/java/nio/channels/Selector.html the documentation for the underlying Java]]
 * API before attempting to use this.
 */
final class Selector(private[nio] val selector: JSelector) extends IOCloseable[Any] {

  def isOpen: UIO[Boolean] = IO.effectTotal(selector.isOpen)

  def provider: UIO[SelectorProvider] =
    IO.effectTotal(selector.provider()).map(new SelectorProvider(_))

  @silent
  def keys: UIO[Set[SelectionKey]] =
    IO.effectTotal(selector.keys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))

  @silent
  def selectedKeys: UIO[Set[SelectionKey]] =
    IO.effectTotal(selector.selectedKeys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))

  def removeKey(key: SelectionKey): UIO[Unit] =
    IO.effectTotal(selector.selectedKeys().remove(key.selectionKey)).unit

  /**
   * Selects a set of keys whose corresponding channels are ready for I/O operations.
   * This method performs a non-blocking selection operation.
   * If no channels have become selectable since the previous selection
   * operation then this method immediately returns zero.
   *
   * @return The number of keys, possibly zero, whose ready-operation sets
   *         were updated by the selection operation.
   */
  def selectNow: IO[IOException, Int] =
    IO.effect(selector.selectNow()).refineToOrDie[IOException]

  /**
   * Performs a blocking select operation.
   *
   * **Note this will very often block**.
   * This is intended to be used when the effect is locked to an Executor
   * that is appropriate for this.
   *
   * Dies with `ClosedSelectorException` if this selector is closed.
   *
   * @return The number of keys, possibly zero, whose ready-operation sets were updated
   */
  def select(timeout: Duration): IO[IOException, Int] =
    IO.effect(selector.select(timeout.toMillis)).refineToOrDie[IOException]

  /**
   * Performs a blocking select operation.
   *
   * **Note this will very often block**.
   * This is intended to be used when the effect is locked to an Executor
   * that is appropriate for this.
   *
   * Dies with `ClosedSelectorException` if this selector is closed.
   *
   * @return The number of keys, possibly zero, whose ready-operation sets were updated
   */
  def select: IO[IOException, Int] =
    IO.effect(selector.select()).refineToOrDie[IOException]

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
  def wakeup: IO[Nothing, Unit] =
    IO.effectTotal(selector.wakeup()).unit

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
  def close: IO[IOException, Unit] =
    IO.effect(selector.close()).refineToOrDie[IOException]

}

object Selector {

  val make: IO[IOException, Selector] =
    IO.effect(new Selector(JSelector.open())).refineToOrDie[IOException]

}
