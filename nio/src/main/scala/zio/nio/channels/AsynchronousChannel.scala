package zio.nio
package channels

import zio._
import zio.clock.Clock
import zio.duration._
import zio.stream.{ Stream, ZSink, ZStream }

import java.io.{ EOFException, IOException }
import java.lang.{ Integer => JInteger, Long => JLong, Void => JVoid }
import java.net.SocketOption
import java.nio.channels.{
  AsynchronousByteChannel => JAsynchronousByteChannel,
  AsynchronousServerSocketChannel => JAsynchronousServerSocketChannel,
  AsynchronousSocketChannel => JAsynchronousSocketChannel,
  Channel => JChannel,
  CompletionHandler
}
import java.util.concurrent.TimeUnit

/**
 * A byte channel that reads and writes asynchronously.
 *
 * The read and write operations will never block the calling thread.
 */
abstract class AsynchronousByteChannel private[channels] (protected val channel: JAsynchronousByteChannel)
    extends Channel {

  import AsynchronousByteChannel._

  /**
   *  Reads data from this channel into buffer, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   */
  final def read(b: ByteBuffer): IO[IOException, Int] =
    effectAsyncChannel[JAsynchronousByteChannel, JInteger](channel)(c => c.read(b.buffer, (), _))
      .flatMap(eofCheck(_))

  final def readChunk(capacity: Int): IO[IOException, Chunk[Byte]] =
    for {
      b <- Buffer.byte(capacity)
      _ <- read(b)
      _ <- b.flip
      r <- b.getChunk()
    } yield r

  /**
   *  Writes data into this channel from buffer, returning the number of bytes written.
   */
  final def write(b: ByteBuffer): IO[IOException, Int] =
    effectAsyncChannel[JAsynchronousByteChannel, JInteger](channel)(c => c.write(b.buffer, (), _)).map(_.toInt)

  /**
   * Writes a chunk of bytes to this channel.
   *
   * More than one write operation may be performed to write out the entire chunk.
   */
  final def writeChunk(chunk: Chunk[Byte]): ZIO[Clock, IOException, Unit] =
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
  ): ZSink[Clock, IOException, Byte, Byte, Long] =
    ZSink {
      for {
        buffer   <- bufferConstruct.toManaged_
        countRef <- Ref.makeManaged(0L)
      } yield (_: Option[Chunk[Byte]])
        .map { chunk =>
          def doWrite(total: Int, c: Chunk[Byte]): ZIO[Any with Clock, IOException, Int] = {
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
          countRef.get.flatMap[Clock, (Either[IOException, Long], Chunk[Byte]), Unit](count =>
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
  ): Stream[IOException, Byte] =
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

object AsynchronousByteChannel {

  private def completionHandlerCallback[A](k: IO[IOException, A] => Unit): CompletionHandler[A, Any] =
    new CompletionHandler[A, Any] {
      def completed(result: A, u: Any): Unit = k(IO.succeedNow(result))

      def failed(t: Throwable, u: Any): Unit =
        t match {
          case e: IOException => k(IO.fail(e))
          case _              => k(IO.die(t))
        }
    }

  /**
   * Encapsulates an asynchronous channel callback into an effect value, with interruption support.
   *
   * If the fiber waiting on the I/O operation is interrupted, the channel is closed, unblocking the fiber.
   */
  private[channels] def effectAsyncChannel[C <: JChannel, A](
    channel: C
  )(op: C => CompletionHandler[A, Any] => Any): IO[IOException, A] =
    IO.effectAsyncInterrupt { k =>
      op(channel)(completionHandlerCallback(k))
      Left(IO.effect(channel.close()).ignore)
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
  def accept: Managed[IOException, AsynchronousSocketChannel] =
    AsynchronousByteChannel
      .effectAsyncChannel[JAsynchronousServerSocketChannel, JAsynchronousSocketChannel](channel)(c => c.accept((), _))
      .map(AsynchronousSocketChannel.fromJava)
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

  def shutdownInput: IO[IOException, Unit] = IO.effect(channel.shutdownInput()).refineToOrDie[IOException].unit

  def shutdownOutput: IO[IOException, Unit] = IO.effect(channel.shutdownOutput()).refineToOrDie[IOException].unit

  def remoteAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getRemoteAddress)
        .map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

  def localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(
      Option(channel.getLocalAddress)
        .map(SocketAddress.fromJava)
    ).refineToOrDie[IOException]

  def connect(socketAddress: SocketAddress): IO[IOException, Unit] =
    AsynchronousByteChannel
      .effectAsyncChannel[JAsynchronousSocketChannel, JVoid](channel)(c =>
        c.connect(socketAddress.jSocketAddress, (), _)
      )
      .unit

  /**
   *  Reads data from this channel into buffer, returning the number of bytes read.
   *
   *  Fails with `java.io.EOFException` if end-of-stream is reached.
   */
  def read(dst: ByteBuffer, timeout: Duration): IO[IOException, Int] =
    AsynchronousByteChannel
      .effectAsyncChannel[JAsynchronousSocketChannel, JInteger](channel) { channel =>
        channel.read(
          dst.buffer,
          timeout.fold(Long.MaxValue, _.toNanos),
          TimeUnit.NANOSECONDS,
          (),
          _
        )
      }
      .flatMap(eofCheck(_))

  def readChunk(capacity: Int, timeout: Duration): IO[IOException, Chunk[Byte]] =
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
  def read(
    dsts: List[ByteBuffer],
    timeout: Duration
  ): IO[IOException, Long] =
    AsynchronousByteChannel
      .effectAsyncChannel[JAsynchronousSocketChannel, JLong](channel) { channel =>
        val a = dsts.map(_.buffer).toArray
        channel.read(
          a,
          0,
          a.length,
          timeout.fold(Long.MaxValue, _.toNanos),
          TimeUnit.NANOSECONDS,
          (),
          _
        )
      }
      .flatMap(eofCheck(_))

  def readChunks(
    capacities: List[Int],
    timeout: Duration
  ): IO[IOException, List[Chunk[Byte]]] =
    IO.foreach(capacities)(Buffer.byte).flatMap { buffers =>
      read(buffers, timeout) *> IO.foreach(buffers)(b => b.flip *> b.getChunk())
    }

  def write(src: ByteBuffer, timeout: Duration): IO[IOException, Int] =
    AsynchronousByteChannel
      .effectAsyncChannel[JAsynchronousSocketChannel, JInteger](channel) { channel =>
        channel.write(src.buffer, timeout.fold(Long.MaxValue, _.toNanos), TimeUnit.NANOSECONDS, (), _)
      }
      .map(_.toInt)

  def writeChunk(chunk: Chunk[Byte], timeout: Duration): IO[IOException, Unit] =
    for {
      b <- Buffer.byte(chunk.length)
      _ <- b.putChunk(chunk)
      _ <- b.flip
      _ <- write(b, timeout)
    } yield ()

  def write(
    srcs: List[ByteBuffer],
    timeout: Duration
  ): IO[IOException, Long] =
    AsynchronousByteChannel
      .effectAsyncChannel[JAsynchronousSocketChannel, JLong](channel) { channel =>
        val a = srcs.map(_.buffer).toArray
        channel.write(
          a,
          0,
          a.length,
          timeout.fold(Long.MaxValue, _.toNanos),
          TimeUnit.NANOSECONDS,
          (),
          _
        )
      }
      .map(_.toLong)

  def writeChunks(chunks: List[Chunk[Byte]], timeout: Duration): IO[IOException, Long] =
    IO.foreach(chunks) { chunk =>
      Buffer.byte(chunk.length).tap(_.putChunk(chunk)).tap(_.flip)
    }.flatMap(write(_, timeout))

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
