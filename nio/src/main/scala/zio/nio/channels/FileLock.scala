package zio.nio.channels

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Trace, UIO, ZIO}

import java.io.IOException
import java.nio.channels.{FileLock => JFileLock}
import java.nio.{channels => jc}

/**
 * A token representing a lock on a region of a file. A file-lock object is created each time a lock is acquired on a
 * file via one of the `lock` or `tryLock` methods of the `FileChannel` class, or the `lock` or `tryLock` methods of the
 * `AsynchronousFileChannel` class.
 */
final class FileLock private[channels] (javaLock: JFileLock) {

  /**
   * The channel upon whose file this lock was acquired. If the underlying NIO channel is a standard channel type, the
   * appropriate ZIO-NIO wrapper class is returned, otherwise a generic [[zio.nio.channels.Channel]] is returned.
   */
  def acquiredBy: Channel =
    javaLock.acquiredBy() match {
      case c: jc.AsynchronousFileChannel =>
        AsynchronousFileChannel.fromJava(c)
      case c: jc.FileChannel =>
        FileChannel.fromJava(c)
      case c =>
        new Channel {
          override protected val channel: jc.Channel = c
        }
    }

  /**
   * Returns the position within the file of the first byte of the locked region. A locked region need not be contained
   * within, or even overlap, the actual underlying file, so the value returned by this method may exceed the file's
   * current size.
   */
  def position: Long = javaLock.position

  /**
   * Returns the size of the locked region in bytes. A locked region need not be contained within, or even overlap, the
   * actual underlying file, so the value returned by this method may exceed the file's current size.
   */
  def size: Long = javaLock.size

  /**
   * Tells whether this lock is shared.
   */
  def isShared: Boolean = javaLock.isShared

  /**
   * Tells whether or not this lock overlaps the given lock range.
   *
   * @param position
   *   The starting position of the lock range
   * @param size
   *   The size of the lock range
   */
  def overlaps(position: Long, size: Long): Boolean = javaLock.overlaps(position, size)

  /**
   * Tells whether or not this lock is valid. A lock object remains valid until it is released or the associated file
   * channel is closed, whichever comes first.
   */
  def isValid(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(javaLock.isValid)

  def release(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.attempt(javaLock.release()).refineToOrDie[IOException]
}

object FileLock {
  def fromJava(javaLock: JFileLock): FileLock = new FileLock(javaLock)
}
