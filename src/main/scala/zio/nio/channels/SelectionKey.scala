package zio.nio.channels

import java.nio.channels.{
  CancelledKeyException,
  SelectableChannel => JSelectableChannel,
  SelectionKey => JSelectionKey
}

import zio.{ IO, UIO }

object SelectionKey {

  val JustCancelledKeyException: PartialFunction[Throwable, CancelledKeyException] = {
    case e: CancelledKeyException => e
  }

}

class SelectionKey(private[nio] val selectionKey: JSelectionKey) {

  import SelectionKey._

  final val channel: UIO[JSelectableChannel] =
    IO.effectTotal(selectionKey.channel())

  final val selector: UIO[Selector] =
    IO.effectTotal(selectionKey.selector()).map(new Selector(_))

  final val isValid: UIO[Boolean] =
    IO.effectTotal(selectionKey.isValid)

  final val cancel: UIO[Unit] =
    IO.effectTotal(selectionKey.cancel())

  final val interestOps: UIO[Int] =
    IO.effectTotal(selectionKey.interestOps())

  final def interestOps(ops: Int): UIO[SelectionKey] =
    IO.effectTotal(selectionKey.interestOps(ops)).map(new SelectionKey(_))

  final val readyOps: UIO[Int] =
    IO.effectTotal(selectionKey.readyOps())

  final def isReadable: IO[CancelledKeyException, Boolean] =
    IO.effect(selectionKey.isReadable()).refineOrDie(JustCancelledKeyException)

  final def isWritable: IO[CancelledKeyException, Boolean] =
    IO.effect(selectionKey.isWritable()).refineOrDie(JustCancelledKeyException)

  final def isConnectable: IO[CancelledKeyException, Boolean] =
    IO.effect(selectionKey.isConnectable()).refineOrDie(JustCancelledKeyException)

  final def isAcceptable: IO[CancelledKeyException, Boolean] =
    IO.effect(selectionKey.isAcceptable()).refineOrDie(JustCancelledKeyException)

  final def attach(ob: Option[AnyRef]): UIO[Option[AnyRef]] =
    IO.effectTotal(Option(selectionKey.attach(ob.orNull)))

  final def attach(ob: AnyRef): UIO[AnyRef] =
    IO.effectTotal(selectionKey.attach(ob))

  final val detach: UIO[Unit] =
    IO.effectTotal(selectionKey.attach(null)).map(_ => ())

  final val attachment: UIO[Option[AnyRef]] =
    IO.effectTotal(selectionKey.attachment()).map(Option(_))

}
