package zio.nio.channels

import com.github.ghik.silencer.silent
import zio.blocking.Blocking
import zio.nio.file.Path
import zio.nio.{ByteBuffer, IOCloseableManagement, MappedByteBuffer}
import zio.{IO, Managed, ZIO}

import java.io.IOException
import java.nio.channels.{FileChannel => JFileChannel}
import java.nio.file.OpenOption
import java.nio.file.attribute.FileAttribute
import scala.collection.JavaConverters._

/**
 * A channel for reading, writing, mapping, and manipulating a file.
 *
 * Unlike network channels, file channels are ''seekable'' with a current position that can be changed. The inherited
 * read and write methods that do not take a position operate at the current position and update the position based on
 * the number of bytes actually read or written.
 */
final class FileChannel private[channels] (protected val channel: JFileChannel) extends BlockingChannel {

  self =>

  type BlockingOps = BlockingFileOps

  final class BlockingFileOps private[FileChannel] () extends GatheringByteOps with ScatteringByteOps {

    override protected[channels] def channel: JFileChannel = self.channel

    /**
     * Truncates this channel's file to the given size. If the given size is less than the file's current size then the
     * file is truncated, discarding any bytes beyond the new end of the file. If the given size is greater than or
     * equal to the file's current size then the file is not modified. In either case, if this channel's file position
     * is greater than the given size then it is set to that size.
     *
     * @param size
     *   The new size, must be >= 0
     */
    def truncate(size: Long): IO[IOException, Unit] = IO.effect(channel.truncate(size)).unit.refineToOrDie[IOException]

    /**
     * Forces any updates to this channel's file to be written to the storage device that contains it.
     *
     * @param metadata
     *   If true then this method is required to force changes to both the file's content and metadata to be written to
     *   storage; otherwise, it need only force content changes to be written
     */
    def force(metadata: Boolean): IO[IOException, Unit] = IO.effect(channel.force(metadata)).refineToOrDie[IOException]

    /**
     * Transfers bytes from this channel's file to the given writable byte channel.
     *
     * @param position
     *   The position within the file at which the transfer is to begin, must be >= 0
     * @param count
     *   The maximum number of bytes to be transferred, must be >= 0
     * @param target
     *   The target channel
     */
    def transferTo(position: Long, count: Long, target: GatheringByteOps): IO[IOException, Long] =
      IO.effect(channel.transferTo(position, count, target.channel)).refineToOrDie[IOException]

    /**
     * Transfers bytes into this channel's file from the given readable byte channel.
     *
     * @param src
     *   The source channel
     * @param position
     *   The position within the file at which the transfer is to begin, must be >= 0
     * @param count
     *   The maximum number of bytes to be transferred, must be >= 0
     */
    def transferFrom(src: ScatteringByteOps, position: Long, count: Long): IO[IOException, Long] =
      IO.effect(channel.transferFrom(src.channel, position, count)).refineToOrDie[IOException]

    /**
     * Reads a sequence of bytes from this channel into the given buffer, starting at the given file position. This
     * method works in the same manner as the `read(ByteBuffer)` method, except that bytes are read starting at the
     * given file position rather than at the channel's current position. This method does not modify this channel's
     * position. If the given position is greater than the file's current size then no bytes are read.
     *
     * @param dst
     *   The buffer to put the read bytes into
     * @param position
     *   The file position at which the transfer is to begin, must be >= 0
     */
    def read(dst: ByteBuffer, position: Long): IO[IOException, Int] =
      dst
        .withJavaBuffer[Any, Throwable, Int](buffer => IO.effect(channel.read(buffer, position)))
        .refineToOrDie[IOException]

    /**
     * Writes a sequence of bytes to this channel from the given buffer, starting at the given file position. This
     * method works in the same manner as the `write(ByteBuffer)` method, except that bytes are written starting at the
     * given file position rather than at the channel's current position. This method does not modify this channel's
     * position. If the given position is greater than the file's current size then the file will be grown to
     * accommodate the new bytes; the values of any bytes between the previous end-of-file and the newly-written bytes
     * are unspecified.
     *
     * @param src
     *   The buffer containing the bytes to write
     * @param position
     *   The file position at which the transfer is to begin, must be >= 0
     * @return
     */
    def write(src: ByteBuffer, position: Long): IO[IOException, Int] =
      src
        .withJavaBuffer[Any, Throwable, Int](buffer => IO.effect(channel.write(buffer, position)))
        .refineToOrDie[IOException]

    /**
     * Maps a region of this channel's file directly into memory.
     *
     * A region of a file may be mapped into memory in one of three modes:
     *   - Read-only: Any attempt to modify the resulting buffer will cause a `ReadOnlyBufferException` to be thrown.
     *     (`MapMode.READ_ONLY`)
     *   - Read/write: Changes made to the resulting buffer will eventually be propagated to the file; they may or may
     *     not be made visible to other programs that have mapped the same file. (`MapMode.READ_WRITE`)
     *   - Private: Changes made to the resulting buffer will not be propagated to the file and will not be visible to
     *     other programs that have mapped the same file; instead, they will cause private copies of the modified
     *     portions of the buffer to be created. (`MapMode.PRIVATE`)
     *
     * @param mode
     *   Indicates if the file is to be mapped read-only, read/write, or private (copy on write).
     * @param position
     *   The position within the file at which the mapped region is to start, must be >= 0
     * @param size
     *   The size of the region to be mapped, must be >= 0 and <= `Int.MaxValue`
     */
    def map(mode: JFileChannel.MapMode, position: Long, size: Long): IO[IOException, MappedByteBuffer] =
      IO.effect(new MappedByteBuffer(channel.map(mode, position, size)))
        .refineToOrDie[IOException]

    /**
     * Acquires a lock on the given region of this channel's file. An invocation of this method will block until the
     * region can be locked, this channel is closed, or the invoking thread is interrupted, whichever comes first.
     *
     * @param position
     *   The position at which the locked region is to start, must be >= 0
     * @param size
     *   The size of the locked region; must be >= 0, and the sum position + size must be >= 0
     * @param shared
     *   true to request a shared lock, in which case this channel must be open for reading (and possibly writing);
     *   false to request an exclusive lock, in which case this channel must be open for writing (and possibly reading)
     */
    def lock(
      position: Long = 0L,
      size: Long = Long.MaxValue,
      shared: Boolean = false
    ): IO[IOException, FileLock] =
      IO.effect(new FileLock(channel.lock(position, size, shared))).refineToOrDie[IOException]

  }

  override def useBlocking[R, E, A](f: BlockingFileOps => ZIO[R, E, A]): ZIO[R with Blocking, E, A] =
    nioBlocking(f(new BlockingOps))

  /**
   * Returns the current value of this channel's position.
   */
  def position: IO[IOException, Long] = IO.effect(channel.position()).refineToOrDie[IOException]

  /**
   * Sets this channel's position. Setting the position to a value that is greater than the file's current size is legal
   * but does not change the size of the file. A later attempt to read bytes at such a position will immediately return
   * an end-of-file indication. A later attempt to write bytes at such a position will cause the file to be grown to
   * accommodate the new bytes; the values of any bytes between the previous end-of-file and the newly-written bytes are
   * unspecified.
   *
   * @param newPosition
   *   The new position, must be >= 0
   */
  def position(newPosition: Long): IO[IOException, Unit] =
    IO.effect(channel.position(newPosition)).unit.refineToOrDie[IOException]

  /**
   * Returns the current size of this channel's file.
   */
  def size: IO[IOException, Long] = IO.effect(channel.size()).refineToOrDie[IOException]

  /**
   * Attempts to acquire a lock on the given region of this channel's file. This method does not block. An invocation
   * always returns immediately, either having acquired a lock on the requested region or having failed to do so. If it
   * fails to acquire a lock because an overlapping lock is held by another program then it returns `None`. If it fails
   * to acquire a lock for any other reason then an appropriate exception is thrown.
   *
   * @param position
   *   The position at which the locked region is to start, must be >= 0
   * @param size
   *   The size of the locked region; must be >= 0, and the sum position + size must be >= 0
   * @param shared
   *   true to request a shared lock, in which case this channel must be open for reading (and possibly writing); false
   *   to request an exclusive lock, in which case this channel must be open for writing (and possibly reading)
   */
  def tryLock(
    position: Long = 0L,
    size: Long = Long.MaxValue,
    shared: Boolean = false
  ): IO[IOException, Option[FileLock]] =
    ZIO.effect(Option(channel.tryLock(position, size, shared)).map(new FileLock(_))).refineToOrDie[IOException]

}

object FileChannel {

  /**
   * Opens or creates a file, returning a file channel to access the file.
   *
   * @param path
   *   The path of the file
   * @param options
   *   Specifies how the file is opened
   * @param attrs
   *   An optional list of file attributes to set atomically when creating the file
   */
  @silent("object JavaConverters in package collection is deprecated")
  def open(
    path: Path,
    options: Set[_ <: OpenOption],
    attrs: FileAttribute[_]*
  ): Managed[IOException, FileChannel] =
    IO.effect(new FileChannel(JFileChannel.open(path.javaPath, options.asJava, attrs: _*)))
      .refineToOrDie[IOException]
      .toNioManaged

  /**
   * Opens or creates a file, returning a file channel to access the file.
   *
   * @param path
   *   The path of the file
   * @param options
   *   Specifies how the file is opened
   */
  def open(path: Path, options: OpenOption*): Managed[IOException, FileChannel] =
    IO.effect(new FileChannel(JFileChannel.open(path.javaPath, options: _*)))
      .refineToOrDie[IOException]
      .toNioManaged

  def fromJava(javaFileChannel: JFileChannel): FileChannel = new FileChannel(javaFileChannel)

  type MapMode = JFileChannel.MapMode

  object MapMode {
    def READ_ONLY: FileChannel.MapMode  = JFileChannel.MapMode.READ_ONLY
    def READ_WRITE: FileChannel.MapMode = JFileChannel.MapMode.READ_WRITE
    def PRIVATE: FileChannel.MapMode    = JFileChannel.MapMode.PRIVATE
  }

}
