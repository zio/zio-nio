package zio.nio.core

import zio.blocking.Blocking
import zio.{ IO, UIO, ZIO }

import java.nio.{ MappedByteBuffer => JMappedByteBuffer }

final class MappedByteBuffer private[nio] (javaBuffer: JMappedByteBuffer) extends ByteBuffer(javaBuffer) {
  def isLoaded: UIO[Boolean] = IO.effectTotal(javaBuffer.isLoaded)

  def load: ZIO[Blocking, Nothing, Unit] = ZIO.accessM(_.get.blocking(IO.effectTotal(javaBuffer.load()).unit))

  def force: ZIO[Blocking, Nothing, Unit] = ZIO.accessM(_.get.blocking(IO.effectTotal(javaBuffer.force()).unit))
}
