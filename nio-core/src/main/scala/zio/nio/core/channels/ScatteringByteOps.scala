package zio.nio.core

package channels

import java.io.IOException
import java.nio.{ ByteBuffer => JByteBuffer }
import java.nio.channels.{ ScatteringByteChannel => JScatteringByteChannel }

import zio.{ Chunk, IO }

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
   * @return The number of bytes read in total, possibly 0
   */
  final def read(dsts: Seq[ByteBuffer]): IO[IOException, Long] =
    IO.effect(channel.read(unwrap(dsts))).refineToOrDie[IOException].flatMap(eofCheck)

  /**
   * Reads a sequence of bytes from this channel into the given buffer.
   *
   * Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   * @return The number of bytes read, possibly 0
   */
  final def read(dst: ByteBuffer): IO[IOException, Int] =
    IO.effect(channel.read(dst.byteBuffer)).refineToOrDie[IOException].flatMap(eofCheck)

  /**
   * Reads a chunk of bytes.
   *
   * Fails with `java.io.EOFException` if end-of-stream is reached.
   *
   * @param capacity The maximum number of bytes to be read.
   * @return The bytes read, between 0 and `capacity` in size, inclusive
   */
  final def readChunk(capacity: Int): IO[IOException, Chunk[Byte]] =
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
   * @param capacities For each int in this sequence, a chunk of that size is produced, if there is enough data in the channel.
   * @return A list with one `Chunk` per input size. Some chunks may be less than the requested size if the channel
   *         does not have enough data
   */
  final def readChunks(capacities: Seq[Int]): IO[IOException, List[Chunk[Byte]]] =
    for {
      buffers <- IO.foreach(capacities)(Buffer.byte)
      _       <- read(buffers)
      chunks  <- IO.foreach(buffers.init)(buf => buf.flip *> buf.getChunk())
    } yield chunks.toList

}

object ScatteringByteOps {

  private def unwrap(dsts: Seq[ByteBuffer]): Array[JByteBuffer] = dsts.map(d => d.byteBuffer).toArray

}
