package zio.nio.core.channels

import java.nio.{ channels => jc }

import zio.{ IO, UIO, ZIO }

object SelectionKey {

  sealed abstract class Operation(val intVal: Int)

  object Operation {
    final case object Read    extends Operation(jc.SelectionKey.OP_READ)
    final case object Write   extends Operation(jc.SelectionKey.OP_WRITE)
    final case object Connect extends Operation(jc.SelectionKey.OP_CONNECT)
    final case object Accept  extends Operation(jc.SelectionKey.OP_ACCEPT)

    final val fullSet: Set[Operation] = Set(Read, Write, Connect, Accept)

    final def fromInt(ops: Int): Set[Operation] =
      fullSet.filter(op => (ops & op.intVal) != 0)

    final def toInt(set: Set[Operation]): Int =
      set.foldLeft(0)((ops, op) => ops | op.intVal)
  }

}

final class SelectionKey(private[nio] val selectionKey: jc.SelectionKey) {
  import SelectionKey._

  def channel: SelectableChannel =
    selectionKey.channel() match {
      case c: jc.SocketChannel       => SocketChannel.NonBlocking.fromJava(c)
      case c: jc.ServerSocketChannel => ServerSocketChannel.NonBlocking.fromJava(c)
      case c: jc.DatagramChannel     => DatagramChannel.NonBlocking.fromJava(c)
      case c: jc.Pipe.SinkChannel    => new Pipe.NonBlockingSinkChannel(c)
      case c: jc.Pipe.SourceChannel  => new Pipe.NonBlockingSourceChannel(c)
      case other                     =>
        new SelectableChannel {
          override protected val channel: jc.SelectableChannel = other
        }
    }

  /**
   * Convenience method for processing keys from the selected key set.
   *
   * Pattern matching on the channel type avoids the need for potentially
   * unsafe casting to the channel type you expect.
   *
   * If a channel type is selected that does not match the pattern match
   * supplied then a defect is raised.
   *
   * Usage:
   *
   * {{{
   *   for {
   *     _ <- selector.select
   *     selectedKeys <- selector.selectedKeys
   *     _ <- IO.foreach_(selectedKeys) { key =>
   *       key.matchChannel { readyOps => {
   *         case channel: ServerSocketChannel if readyOps(Operation.Accept) =>
   *           // use `channel` to accept connection
   *         case channel: SocketChannel =>
   *           IO.when(readyOps(Operation.Read)) {
   *             // use `channel` to read
   *           } *>
   *             IO.when(readyOps(Operation.Write)) {
   *               // use `channel` to write
   *             }
   *       } } *> selector.removeKey(key)
   *     }
   *   } yield ()
   * }}}
   *
   * @param matcher Function that is passed the ready operations set, and
   *                must return a partial function that handles whatever
   *                channel types are registered with the selector.
   * @return The effect value returned by `matcher`, or a defect value if
   *         `matcher` did not match the selected channel.
   */
  def matchChannel[R, E, A](
    matcher: Set[Operation] => PartialFunction[SelectableChannel, ZIO[R, E, A]]
  ): ZIO[R, E, A] =
    readyOps.flatMap(
      matcher(_)
        .applyOrElse(
          channel,
          (channel: SelectableChannel) => ZIO.dieMessage(s"Unexpected channel type: $channel")
        )
    )

  final def selector: Selector =
    new Selector(selectionKey.selector())

  final def isValid: UIO[Boolean] =
    IO.effectTotal(selectionKey.isValid)

  final def cancel: UIO[Unit] =
    IO.effectTotal(selectionKey.cancel())

  final def interestOps: UIO[Set[Operation]] =
    IO.effectTotal(Operation.fromInt(selectionKey.interestOps()))

  final def interestOps(ops: Set[Operation]): UIO[Unit] =
    IO.effectTotal(selectionKey.interestOps(Operation.toInt(ops))).unit

  def interested(op: Operation): UIO[Set[Operation]] =
    for {
      current    <- interestOps
      newInterest = current + op
      _          <- interestOps(newInterest)
    } yield newInterest

  def notInterested(op: Operation): UIO[Set[Operation]] =
    for {
      current    <- interestOps
      newInterest = current - op
      _          <- interestOps(newInterest)
    } yield newInterest

  final def readyOps: UIO[Set[Operation]] =
    IO.effectTotal(Operation.fromInt(selectionKey.readyOps()))

  final def isReadable: UIO[Boolean] =
    IO.effectTotal(selectionKey.isReadable())

  final def isWritable: UIO[Boolean] =
    IO.effectTotal(selectionKey.isWritable())

  final def isConnectable: UIO[Boolean] =
    IO.effectTotal(selectionKey.isConnectable())

  final def isAcceptable: UIO[Boolean] =
    IO.effectTotal(selectionKey.isAcceptable())

  final def attach(ob: Option[AnyRef]): UIO[Option[AnyRef]] =
    IO.effectTotal(Option(selectionKey.attach(ob.orNull)))

  final def attachment: UIO[Option[AnyRef]] =
    IO.effectTotal(selectionKey.attachment()).map(Option(_))

  override def toString: String = selectionKey.toString()

}
