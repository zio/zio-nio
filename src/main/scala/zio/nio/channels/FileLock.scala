package zio.nio.channels

import java.io.IOException
import java.nio.channels.{ FileLock => JFileLock }
import zio.{ IO, UIO }

class FileLock(private val fileLock: JFileLock, private val channel: AsynchronousFileChannel) {

  final def acquiredBy(): UIO[AsynchronousFileChannel] =
    IO.effectTotal(channel)

  final def position: UIO[Long] =
    IO.effectTotal(fileLock.position)

  final val size: UIO[Long] =
    IO.effectTotal(fileLock.size)

  final def isShared: UIO[Boolean] =
    IO.effectTotal(fileLock.isShared)

  final def overlaps(position: Long, size: Long): UIO[Boolean] =
    IO.effectTotal(fileLock.overlaps(position, size))

  final def isValid: UIO[Boolean] =
    IO.effectTotal(fileLock.isValid)

  final def release: IO[IOException, Unit] =
    IO.effect(fileLock.release()).refineToOrDie[IOException]

  final override def toString: String = fileLock.toString

}

object FileLock {
  final private[nio] def fromJFileLock(jFileLock: JFileLock, channel: AsynchronousFileChannel) =
    new FileLock(jFileLock, channel)
}
