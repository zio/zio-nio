package zio.nio.core

import java.nio.{ MappedByteBuffer => JMappedByteBuffer }

import zio.{ IO, UIO, ZIO }
import zio.blocking.Blocking

final class MappedByteBuffer private[nio] (javaBuffer: JMappedByteBuffer) extends ByteBuffer(javaBuffer) {
  def isLoaded: UIO[Boolean] = IO.effectTotal(javaBuffer.isLoaded)

  def load: ZIO[Blocking, Nothing, Unit] = ZIO.accessM(_.get.blocking(IO.effectTotal(javaBuffer.load()).unit))

  def force: ZIO[Blocking, Nothing, Unit] = ZIO.accessM(_.get.blocking(IO.effectTotal(javaBuffer.force()).unit))
}
