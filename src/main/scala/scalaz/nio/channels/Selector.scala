package scalaz.nio.channels

import java.nio.channels.{Selector => JSelector}

import scalaz.nio.channels.spi.SelectorProvider
import scalaz.zio.IO

import scala.collection.JavaConverters

class Selector(private val selector: JSelector) {

  def isOpen: IO[Nothing, Boolean] = IO.now(selector.isOpen)

  def provider(): IO[Exception, SelectorProvider] =
    IO.syncException(selector.provider()).map(new SelectorProvider(_))

  def keys: IO[Exception, Set[SelectionKey]] =
    IO.syncException(selector.keys()).map { keys =>
      JavaConverters.asScalaSet(keys).toSet.map(new SelectionKey(_))
    }

  def selectedKeys: IO[Exception, Set[SelectionKey]] =
    IO.syncException(selector.selectedKeys()).map { keys =>
      JavaConverters.asScalaSet(keys).toSet.map(new SelectionKey(_))
    }

  def selectNow(): IO[Exception, Int] =
    IO.syncException(selector.selectNow())

  def select(timeout: Long): IO[Exception, Int] =
    IO.syncException(selector.select(timeout))

  def select(): IO[Exception, Int] =
    IO.syncException(selector.select())

  def wakeup(): IO[Exception, Selector] =
    IO.syncException(selector.wakeup()).map(new Selector(_))

  def close(): IO[Exception, Unit] =
    IO.syncException(selector.close()).void
}

object Selector {

  def apply(): IO[Exception, Selector] =
    IO.syncException(JSelector.open())
      .map(new Selector(_))

}
