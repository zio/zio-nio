package zio.nio
package channels

import java.io.{ EOFException, IOException }
import java.lang.{ Integer => JInteger, Long => JLong, Void => JVoid }
import java.net.SocketOption
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel
}
import java.util.concurrent.TimeUnit

import zio.clock.Clock
import zio.duration._
import zio.interop.javaz._
import zio.stream.{ Stream, ZSink, ZStream }
import zio._

/**
 * A byte channel that reads and writes asynchronously.
 *
 * The read and write operations will never block the calling thread.
 */
abstract class AsynchronousByteChannel private[channels] (protected val channel: JAsynchronousByteChannel)
    extends Channel {

  /**
   *  Reads data from this channel into buffer, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   */
  final def read(b: ByteBuffer): IO[Exception, Int] =
    effectAsyncWithCompletionHandler[JInteger](h => channel.read(b.buffer, (), h))
      .refineToOrDie[Exception]
      .flatMap(eofCheck(_))

  final def readChunk(capacity: Int): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- read(b)
      _ <- b.flip
      r <- b.getChunk()
    } yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write(b: ByteBuffer): IO[Exception, Int] =
    effectAsyncWithCompletionHandler[JInteger](h => channel.write(b.buffer, (), h))
      .map(_.toInt)
      .refineToOrDie[Exception]

  /**
   * Writes a chunk of bytes to this channel.
   *
   * More than one write operation may be performed to write out the entire chunk.
   */
  final def writeChunk(chunk: Chunk[Byte]): ZIO[Clock, Exception, Unit] =
    for {
      b <- Buffer.byte(chunk)
      _ <- write(b).repeatWhileM(_ => b.hasRemaining)
    } yield ()

  /**
   * A sink that will write all the bytes it receives to this channel.
   *
   * @param bufferConstruct Optional, overrides how to construct the buffer used to transfer bytes received by the sink to this channel.
   */
  def sink(
    bufferConstruct: UIO[ByteBuffer] = Buffer.byte(5000)
  ): ZSink[Clock, Exception, Byte, Byte, Long] =
    ZSink {
      for {
        buffer   <- bufferConstruct.toManaged_
        countRef <- Ref.makeManaged(0L)
      } yield (_: Option[Chunk[Byte]])
        .map { chunk =>
          def doWrite(total: Int, c: Chunk[Byte]): ZIO[Any with Clock, Exception, Int] = {
            val x = for {
              remaining <- buffer.putChunk(c)
              _         <- buffer.flip
              count     <- ZStream
                             .repeatEffectWith(write(buffer), Schedule.recurWhileM(Function.const(buffer.hasRemaining)))
                             .runSum
              _         <- buffer.clear
            } yield (count + total, remaining)
            x.flatMap {
              case (result, remaining) if remaining.isEmpty => ZIO.succeed(result)
              case (result, remaining)                      => doWrite(result, remaining)
            }
          }

          doWrite(0, chunk).foldM(
            e => buffer.getChunk().flatMap(c => ZIO.fail((Left(e), c))),
            count => countRef.update(_ + count.toLong)
          )
        }
        .getOrElse(
          countRef.get.flatMap[Clock, (Either[Exception, Long], Chunk[Byte]), Unit](count =>
            ZIO.fail((Right(count), Chunk.empty))
          )
        )
    }

  /**
   * A `ZStream` that reads from this channel.
   * The stream terminates without error if the channel reaches end-of-stream.
   *
   * @param bufferConstruct Optional, overrides how to construct the buffer used to transfer bytes read from this channel into the stream.
   */
  def stream(
    bufferConstruct: UIO[ByteBuffer] = Buffer.byte(5000)
  ): Stream[Exception, Byte] =
    ZStream {
      bufferConstruct.toManaged_
        .map { buffer =>
          val doRead = for {
            _     <- read(buffer)
            _     <- buffer.flip
            chunk <- buffer.getChunk()
            _     <- buffer.clear
          } yield chunk
          doRead.mapError {
            case _: EOFException => None
            case e               => Some(e)
          }
        }
    }

}

final class AsynchronousServerSocketChannel(protected val channel: JAsynchronousServerSocketChannel) extends Channel {

  def bindTo(local: SocketAddress, backlog: Int = 0): IO[IOException, Unit] = bind(Some(local), backlog)

  def bindAuto(backlog: Int = 0): IO[IOException, Unit] = bind(None, backlog)

  /**
   * Binds the channel's socket to a local address and configures the socket
   * to listen for connections, up to backlog pending connection.
   */
  def bind(address: Option[SocketAddress], backlog: Int = 0): IO[IOException, Unit] =
    IO.effect(channel.bind(address.map(_.jSocketAddress).orNull, backlog)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  /**
   * Accepts a connection.
   */
  val accept: Managed[Exception, AsynchronousSocketChannel] =
    effectAsyncWithCompletionHandler[JAsynchronousSocketChannel](h => channel.accept((), h))
      .map(AsynchronousSocketChannel.fromJava)
      .refineToOrDie[Exception]
      .toNioManaged

  /**
   * The `SocketAddress` that the socket is bound to,
   * or the `SocketAddress` representing the loopback address if
   * denied by the security manager, or `Maybe.empty` if the
   * channel's socket is not bound.
   */
  def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getLocalAddress).map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

}

object AsynchronousServerSocketChannel {

  def open: Managed[IOException, AsynchronousServerSocketChannel] =
    IO.effect(new AsynchronousServerSocketChannel(JAsynchronousServerSocketChannel.open()))
      .refineToOrDie[IOException]
      .toNioManaged

  def open(
    channelGroup: AsynchronousChannelGroup
  ): Managed[IOException, AsynchronousServerSocketChannel] =
    IO.effect(new AsynchronousServerSocketChannel(JAsynchronousServerSocketChannel.open(channelGroup.channelGroup)))
      .refineToOrDie[IOException]
      .toNioManaged

  def fromJava(channel: JAsynchronousServerSocketChannel): AsynchronousServerSocketChannel =
    new AsynchronousServerSocketChannel(channel)

}

final class AsynchronousSocketChannel(override protected val channel: JAsynchronousSocketChannel)
    extends AsynchronousByteChannel(channel) {

  def bindTo(address: SocketAddress): IO[IOException, Unit] = bind(Some(address))

  def bindAuto: IO[IOException, Unit] = bind(None)

  def bind(address: Option[SocketAddress]): IO[IOException, Unit] =
    IO.effect(channel.bind(address.map(_.jSocketAddress).orNull)).refineToOrDie[IOException].unit

  def setOption[T](name: SocketOption[T], value: T): IO[IOException, Unit] =
    IO.effect(channel.setOption(name, value)).refineToOrDie[IOException].unit

  final def shutdownInput: IO[IOException, Unit] = IO.effect(channel.shutdownInput()).refineToOrDie[IOException].unit

  final def shutdownOutput: IO[IOException, Unit] = IO.effect(channel.shutdownOutput()).refineToOrDie[IOException].unit

  final def remoteAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getRemoteAddress)
        .map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

  final def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getLocalAddress)
        .map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

  final def connect(socketAddress: SocketAddress): IO[Exception, Unit] =
    effectAsyncWithCompletionHandler[JVoid](h => channel.connect(socketAddress.jSocketAddress, (), h)).unit
      .refineToOrDie[Exception]

  /**
   *  Reads data from this channel into buffer, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   */
  final def read[A](dst: ByteBuffer, timeout: Duration): IO[Exception, Int] =
    effectAsyncWithCompletionHandler[JInteger] { h =>
      channel.read(
        dst.buffer,
        timeout.fold(Long.MaxValue, _.toNanos),
        TimeUnit.NANOSECONDS,
        (),
        h
      )
    }
      .refineToOrDie[Exception]
      .flatMap(eofCheck(_))

  final def readChunk[A](capacity: Int, timeout: Duration): IO[Exception, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- read(b, timeout)
      _ <- b.flip
      r <- b.getChunk()
    } yield r

  /**
   *  Reads data from this channel into a set of buffers, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   */
  final def read[A](
    dsts: List[ByteBuffer],
    timeout: Duration
  ): IO[Exception, Long] =
    effectAsyncWithCompletionHandler[JLong] { h =>
      val a = dsts.map(_.buffer).toArray
      channel.read(
        a,
        0,
        a.length,
        timeout.fold(Long.MaxValue, _.toNanos),
        TimeUnit.NANOSECONDS,
        (),
        h
      )
    }
      .refineToOrDie[Exception]
      .flatMap(eofCheck(_))

  final def readChunks[A](
    capacities: List[Int],
    timeout: Duration
  ): IO[Exception, List[Chunk[Byte]]] =
    for {
      bs     <- ZIO.foreach(capacities)(Buffer.byte)
      _      <- read(bs, timeout)
      chunks <- ZIO.foreach(bs)(b => b.flip *> b.getChunk())
    } yield chunks
}

object AsynchronousSocketChannel {

  def open: Managed[IOException, AsynchronousSocketChannel] =
    IO.effect(new AsynchronousSocketChannel(JAsynchronousSocketChannel.open()))
      .refineToOrDie[IOException]
      .toNioManaged

  def open(channelGroup: AsynchronousChannelGroup): Managed[IOException, AsynchronousSocketChannel] =
    IO.effect(new AsynchronousSocketChannel(JAsynchronousSocketChannel.open(channelGroup.channelGroup)))
      .refineToOrDie[IOException]
      .toNioManaged

  def fromJava(asyncSocketChannel: JAsynchronousSocketChannel): AsynchronousSocketChannel =
    new AsynchronousSocketChannel(asyncSocketChannel)

}
