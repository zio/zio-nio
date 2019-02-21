package scalaz.nio.channels

import java.nio.channels.{ SelectableChannel => JSelectableChannel, SelectionKey => JSelectionKey }

import scalaz.zio.IO

class SelectionKey(private[nio] val selectionKey: JSelectionKey) {

  final val channel: IO[Nothing, JSelectableChannel] =
    IO.sync(selectionKey.channel())

  final val selector: IO[Nothing, Selector] =
    IO.sync(selectionKey.selector()).map(new Selector(_))

  final val isValid: IO[Nothing, Boolean] =
    IO.sync(selectionKey.isValid)

  final val cancel: IO[Nothing, Unit] =
    IO.sync(selectionKey.cancel())

  final val interestOps: IO[Nothing, Int] =
    IO.sync(selectionKey.interestOps())

  final def interestOps(ops: Int): IO[Nothing, SelectionKey] =
    IO.sync(selectionKey.interestOps(ops)).map(new SelectionKey(_))

  final val readyOps: IO[Nothing, Int] =
    IO.sync(selectionKey.readyOps())

  final def isReadable: Boolean =
    selectionKey.isReadable()

  final def isWritable: Boolean =
    selectionKey.isWritable()

  final def isConnectable: Boolean =
    selectionKey.isConnectable()

  final def isAcceptable: Boolean =
    selectionKey.isAcceptable()

  final def attach(ob: Option[AnyRef]): IO[Nothing, Option[AnyRef]] =
    IO.sync(Option(selectionKey.attach(ob.orNull)))

  final def attach(ob: AnyRef): IO[Nothing, AnyRef] =
    IO.sync(selectionKey.attach(ob))

  final val detach: IO[Nothing, Unit] =
    IO.sync(selectionKey.attach(null)).map(_ => ())

  final val attachment: IO[Nothing, Option[AnyRef]] =
    IO.sync(selectionKey.attachment()).map(Option(_))

}
