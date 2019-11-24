package zio.nio.core.channels

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

  sealed abstract class Operation(val intVal: Int)

  object Operation {
    final case object Read    extends Operation(JSelectionKey.OP_READ)
    final case object Write   extends Operation(JSelectionKey.OP_WRITE)
    final case object Connect extends Operation(JSelectionKey.OP_CONNECT)
    final case object Accept  extends Operation(JSelectionKey.OP_ACCEPT)

    final val fullSet: Set[Operation] = Set(Read, Write, Connect, Accept)

    final def fromInt(ops: Int): Set[Operation] =
      fullSet.filter(op => (ops & op.intVal) != 0)

    final def toInt(set: Set[Operation]): Int =
      set.foldLeft(0)((ops, op) => ops | op.intVal)
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

  final val interestOps: IO[CancelledKeyException, Set[Operation]] =
    IO.effect(selectionKey.interestOps())
      .map(Operation.fromInt(_))
      .refineToOrDie[CancelledKeyException]

  final def interestOps(ops: Set[Operation]): IO[CancelledKeyException, Unit] =
    IO.effect(selectionKey.interestOps(Operation.toInt(ops)))
      .unit
      .refineToOrDie[CancelledKeyException]

  final val readyOps: IO[CancelledKeyException, Set[Operation]] =
    IO.effect(selectionKey.readyOps())
      .map(Operation.fromInt(_))
      .refineToOrDie[CancelledKeyException]

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
