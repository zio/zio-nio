package zio.nio.core.channels

import java.io.IOException
import java.nio.channels.{ FileLock => JFileLock }

import zio.{ IO, UIO }
import zio.nio.core.IOCloseable

final class FileLock private[channels] (javaLock: JFileLock) extends IOCloseable[Any] {

  def acquiredBy: Channel =
    new Channel {
      override protected val channel = javaLock.acquiredBy
    }

  def position: Long = javaLock.position

  def size: Long = javaLock.size

  def isShared: Boolean = javaLock.isShared

  def overlaps(position: Long, size: Long): Boolean = javaLock.overlaps(position, size)

  def isValid: UIO[Boolean] = UIO.effectTotal(javaLock.isValid())

  /**
   * Releases this lock.
   *
   * If this lock object is valid then invoking this method releases the lock and renders the object invalid.
   * If this lock object is invalid then invoking this method has no effect.
   */
  def release: IO[IOException, Unit] = IO.effect(javaLock.release()).refineToOrDie[IOException]

  /**
   * Closes this file lock.
   *
   * Alias for `release`.
   */
  override def close: IO[IOException, Unit] = release
}

object FileLock {
  def fromJava(javaLock: JFileLock): FileLock = new FileLock(javaLock)
}
