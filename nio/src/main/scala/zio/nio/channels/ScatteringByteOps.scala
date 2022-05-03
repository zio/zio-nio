package zio.nio

package channels

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.{Stream, ZStream}
import zio.{Chunk, IO, Trace, UIO, ZIO}

import java.io.{EOFException, IOException}
import java.nio.channels.{ScatteringByteChannel => JScatteringByteChannel}
import java.nio.{ByteBuffer => JByteBuffer}

/**
 * A channel that can read bytes into a sequence of buffers.
 */
trait ScatteringByteOps {

  import ScatteringByteOps._

  protected[channels] def channel: JScatteringByteChannel

  /**
   * Reads a sequence of bytes from this channel into the provided list of buffers, in order.
   *
   * Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   * @return
   *   The number of bytes read in total, possibly 0
   */
  final def read(dsts: Seq[ByteBuffer])(implicit trace: Trace): IO[IOException, Long] =
    ZIO.attempt(channel.read(unwrap(dsts))).refineToOrDie[IOException].flatMap(eofCheck)

  /**
   * Reads a sequence of bytes from this channel into the given buffer.
   *
   * Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   * @return
   *   The number of bytes read, possibly 0
   */
  final def read(dst: ByteBuffer)(implicit trace: Trace): IO[IOException, Int] =
    ZIO.attempt(channel.read(dst.buffer)).refineToOrDie[IOException].flatMap(eofCheck)

  /**
   * Reads a chunk of bytes.
   *
   * Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   * @param capacity
   *   The maximum number of bytes to be read.
   * @return
   *   The bytes read, between 0 and `capacity` in size, inclusive
   */
  final def readChunk(capacity: Int)(implicit trace: Trace): IO[IOException, Chunk[Byte]] =
    for {
      buffer <- Buffer.byte(capacity)
      _      <- read(buffer)
      _      <- buffer.flip
      chunk  <- buffer.getChunk()
    } yield chunk

  /**
   * Reads a sequence of bytes grouped into multiple chunks.
   *
   * Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   * @param capacities
   *   For each int in this sequence, a chunk of that size is produced, if there is enough data in the channel.
   * @return
   *   A list with one `Chunk` per input size. Some chunks may be less than the requested size if the channel does not
   *   have enough data
   */
  final def readChunks(capacities: Seq[Int])(implicit trace: Trace): IO[IOException, List[Chunk[Byte]]] =
    for {
      buffers <- ZIO.foreach(capacities)(Buffer.byte)
      _       <- read(buffers)
      chunks  <- ZIO.foreach(buffers.init)(buf => buf.flip *> buf.getChunk())
    } yield chunks.toList

  def stream()(implicit trace: Trace): Stream[IOException, Byte] = stream(Buffer.byte(5000))

  /**
   * A `ZStream` that reads from this channel. '''Note:''' This method does not work well with a channel in non-blocking
   * mode, as it will busy-wait whenever the channel is not ready for reads. The returned stream should be run within
   * the context of a `useBlocking` call for correct blocking and interruption support.
   *
   * The stream terminates without error if the channel reaches end-of-stream.
   *
   * @param bufferConstruct
   *   Optional, overrides how to construct the buffer used to transfer bytes read from this channel into the stream. By
   *   default a heap buffer is used, but a direct buffer will usually perform better.
   */
  def stream(
    bufferConstruct: UIO[ByteBuffer]
  )(implicit trace: Trace): Stream[IOException, Byte] =
    ZStream.unwrap {
      bufferConstruct.map { buffer =>
        val doRead = for {
          _     <- read(buffer)
          _     <- buffer.flip
          chunk <- buffer.getChunk()
          _     <- buffer.clear
        } yield chunk
        ZStream.repeatZIOChunkOption(
          doRead.mapError {
            case _: EOFException => None
            case e               => Some(e)
          }
        )
      }
    }
}

object ScatteringByteOps {

  private def unwrap(dsts: Seq[ByteBuffer]): Array[JByteBuffer] = dsts.map(d => d.buffer).toArray

}
