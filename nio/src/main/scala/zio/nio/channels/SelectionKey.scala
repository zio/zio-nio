package zio.nio.channels

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Trace, UIO, ZIO}

import java.nio.{channels => jc}

object SelectionKey {

  sealed abstract class Operation(val intVal: Int)

  object Operation {
    case object Read    extends Operation(jc.SelectionKey.OP_READ)
    case object Write   extends Operation(jc.SelectionKey.OP_WRITE)
    case object Connect extends Operation(jc.SelectionKey.OP_CONNECT)
    case object Accept  extends Operation(jc.SelectionKey.OP_ACCEPT)

    final val fullSet: Set[Operation] = Set(Read, Write, Connect, Accept)

    final def fromInt(ops: Int): Set[Operation] = fullSet.filter(op => (ops & op.intVal) != 0)

    final def toInt(set: Set[Operation]): Int = set.foldLeft(0)((ops, op) => ops | op.intVal)
  }

}

final class SelectionKey(private[nio] val selectionKey: jc.SelectionKey) {
  import SelectionKey._

  def channel: SelectableChannel =
    selectionKey.channel() match {
      case c: jc.SocketChannel       => new SocketChannel(c)
      case c: jc.ServerSocketChannel => new ServerSocketChannel(c)
      case c: jc.DatagramChannel     => new DatagramChannel(c)
      case c: jc.Pipe.SinkChannel    => new Pipe.SinkChannel(c)
      case c: jc.Pipe.SourceChannel  => new Pipe.SourceChannel(c)
      case other =>
        new SelectableChannel {
          override protected val channel: jc.SelectableChannel = other

          override type BlockingOps = Nothing

          override type NonBlockingOps = Nothing

          override protected def makeBlockingOps = ???

          override protected def makeNonBlockingOps = ???

        }
    }

  /**
   * Convenience method for processing keys from the selected key set.
   *
   * Pattern matching on the channel type avoids the need for potentially unsafe casting to the channel type you expect.
   *
   * If a channel type is selected that does not match the pattern match supplied then a defect is raised.
   *
   * Usage:
   *
   * {{{
   *   for {
   *     _ <- selector.select
   *     _ <- selector.foreachSelectedKey { key =>
   *       key.matchChannel { readyOps => {
   *         case channel: ServerSocketChannel if readyOps(Operation.Accept) =>
   *           // use `channel` to accept connection
   *         case channel: SocketChannel =>
   *           ZIO.when(readyOps(Operation.Read)) {
   *             // use `channel` to read
   *           } *>
   *             ZIO.when(readyOps(Operation.Write)) {
   *               // use `channel` to write
   *             }
   *       } }
   *     }
   *   } yield ()
   * }}}
   *
   * @param matcher
   *   Function that is passed the ready operations set, and must return a partial function that handles whatever
   *   channel types are registered with the selector.
   * @return
   *   The effect value returned by `matcher`, or a defect value if `matcher` did not match the selected channel.
   */
  def matchChannel[R, E, A](
    matcher: Set[Operation] => PartialFunction[SelectableChannel, ZIO[R, E, A]]
  )(implicit trace: Trace): ZIO[R, E, A] =
    readyOps.flatMap(
      matcher(_)
        .applyOrElse(channel, (channel: SelectableChannel) => ZIO.dieMessage(s"Unexpected channel type: $channel"))
    )

  final def selector: Selector = new Selector(selectionKey.selector())

  final def isValid(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(selectionKey.isValid)

  final def cancel(implicit trace: Trace): UIO[Unit] = ZIO.succeed(selectionKey.cancel())

  final def interestOps(implicit trace: Trace): UIO[Set[Operation]] =
    ZIO.succeed(Operation.fromInt(selectionKey.interestOps()))

  final def interestOps(ops: Set[Operation])(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(selectionKey.interestOps(Operation.toInt(ops))).unit

  def interested(op: Operation)(implicit trace: Trace): UIO[Set[Operation]] =
    for {
      current    <- interestOps
      newInterest = current + op
      _          <- interestOps(newInterest)
    } yield newInterest

  def notInterested(op: Operation)(implicit trace: Trace): UIO[Set[Operation]] =
    for {
      current    <- interestOps
      newInterest = current - op
      _          <- interestOps(newInterest)
    } yield newInterest

  final def readyOps(implicit trace: Trace): UIO[Set[Operation]] =
    ZIO.succeed(Operation.fromInt(selectionKey.readyOps()))

  final def isReadable(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(selectionKey.isReadable())

  final def isWritable(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(selectionKey.isWritable())

  final def isConnectable(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(selectionKey.isConnectable())

  final def isAcceptable(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(selectionKey.isAcceptable())

  final def attach(ob: Option[AnyRef])(implicit trace: Trace): UIO[Option[AnyRef]] =
    ZIO.succeed(Option(selectionKey.attach(ob.orNull)))

  final def attachment(implicit trace: Trace): UIO[Option[AnyRef]] =
    ZIO.succeed(selectionKey.attachment()).map(Option(_))

  override def toString: String = selectionKey.toString

  override def hashCode(): Int = selectionKey.hashCode()

  override def equals(obj: Any): Boolean =
    obj match {
      case other: SelectionKey => selectionKey.equals(other.selectionKey)
      case _                   => false
    }

}
