package zio.nio.core

package channels

import java.io.IOException
import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }

import zio.{ Chunk, IO }

/**
 * A channel that can read bytes into a sequence of buffers.
 */
trait ScatteringByteChannel extends Channel {

  import ScatteringByteChannel._

  override protected[channels] val channel: JScatteringByteChannel

  /**
   * Reads a sequence of bytes from this channel into the provided list of buffers, in order.
   *
   * @return The number of bytes read in total, possibly 0, or None if end-of-stream is reached.
   */
  final def read(dsts: List[ByteBuffer]): IO[IOException, Option[Long]] =
    IO.effect(channel.read(unwrap(dsts)).eofCheck).refineToOrDie[IOException]

  /**
   * Reads a sequence of bytes from this channel into the given buffer.
   *
   * @return The number of bytes read, possibly 0, or None if end-of-stream is reached.
   */
  final def read(dst: ByteBuffer): IO[IOException, Option[Int]] =
    IO.effect(channel.read(dst.byteBuffer).eofCheck).refineToOrDie[IOException]

  /**
   * Reads a chunk of bytes.
   *
   * @param capacity The maximum number of bytes to be read.
   * @return The bytes read, between 0 and `capacity` in size, inclusive, or `None` if end-of-stream is reached.
   */
  final def readChunk(capacity: Int): IO[IOException, Option[Chunk[Byte]]] = {
    val result = for {
      buffer <- Buffer.byte(capacity)
      _      <- read(buffer).some
      _      <- buffer.flip
      chunk  <- buffer.getChunk()
    } yield chunk
    result.optional
  }

  /**
   * Reads a sequence of bytes grouped into multiple chunks.
   *
   * @param capacities For each int in this sequence, a chunk of that size is produced, if there is enough data in the channel.
   * @return A list with one `Chunk` per input size. Some chunks may be less than the requested size if the channel
   *         does not have enough data. None is returned if end-of-stream is reached.
   */
  final def read(capacities: Seq[Int]): IO[IOException, Option[List[Chunk[Byte]]]] = {
    val result = for {
      buffers <- IO.foreach(capacities)(Buffer.byte)
      _       <- read(buffers).some
      chunks  <- IO.foreach(buffers.init)(buf => buf.flip *> buf.getChunk())
    } yield chunks
    result.optional
  }

}

object ScatteringByteChannel {

  private def unwrap(dsts: List[ByteBuffer]): Array[JByteBuffer] =
    dsts.map(d => d.byteBuffer).toArray

}
