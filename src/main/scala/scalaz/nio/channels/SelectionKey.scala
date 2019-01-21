package scalaz.nio.channels

import java.nio.channels.{SelectableChannel, SelectionKey => JSelectionKey}

import scalaz.zio.IO

class SelectionKey(private val selectionKey: JSelectionKey) {

  def channel(): IO[Nothing, SelectableChannel] = // TODO: wrapper for SelectableChannel
    IO.sync(selectionKey.channel())

  def selector(): IO[Nothing, Selector] =
    IO.sync(selectionKey.selector()).map(new Selector(_))

  def isValid: IO[Nothing, Boolean] =
    IO.sync(selectionKey.isValid)

  def cancel(): IO[Exception, Unit] =
    IO.syncException(selectionKey.cancel())

  def interestOps(): IO[Exception, Int] =
    IO.syncException(selectionKey.interestOps())

  def interestOps(ops: Int): IO[Exception, SelectionKey] =
    IO.syncException(selectionKey.interestOps(ops)).map(new SelectionKey(_))

  def readyOps(): IO[Exception, Int] =
    IO.syncException(selectionKey.readyOps())

  def isReadable(): IO[Exception, Boolean] =
    IO.syncException(selectionKey.isReadable)

  def isWritable(): IO[Exception, Boolean] =
    IO.syncException(selectionKey.isWritable)

  def isConnectable(): IO[Exception, Boolean] =
    IO.syncException(selectionKey.isConnectable)

  def isAcceptable(): IO[Exception, Boolean] =
    IO.syncException(selectionKey.isAcceptable)

  def attach(ob: AnyRef): IO[Exception, AnyRef] =
    IO.syncException(selectionKey.attach(ob))

  def attachment: IO[Exception, Option[AnyRef]] =
    IO.syncException(selectionKey.attachment()).map(Option(_))

}
