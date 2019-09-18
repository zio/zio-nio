package zio.nio

import zio.IO

import zio.blocking._

import javax.net.ssl.{ SSLEngine => JSSLEngine }

import javax.net.ssl.SSLEngineResult

import java.nio.{ ByteBuffer => JByteBuffer }
import java.lang.Runnable

class SSLEngine(val engine: JSSLEngine) {

  final def wrap(src: Buffer[Byte], dst: Buffer[Byte]): IO[Exception, SSLEngineResult] =
    IO.effect(engine.wrap(src.buffer.asInstanceOf[JByteBuffer], dst.buffer.asInstanceOf[JByteBuffer]))
      .refineToOrDie[Exception]

  final def unwrap(src: Buffer[Byte], dst: Buffer[Byte]): IO[Exception, SSLEngineResult] =
    IO.effect(engine.unwrap(src.buffer.asInstanceOf[JByteBuffer], dst.buffer.asInstanceOf[JByteBuffer]))
      .refineToOrDie[Exception]

  final def closeInbound() = IO.effect(engine.closeInbound()).refineToOrDie[Exception]

  final def closeOutbound() = IO.effect(engine.closeOutbound()).refineToOrDie[Exception]

  final def isOutboundDone() = IO.effect(engine.isOutboundDone).refineToOrDie[Exception]

  final def isInboundDone() = IO.effect(engine.isInboundDone).refineToOrDie[Exception]

  final def setUseClientMode(use: Boolean) = IO.effect(engine.setUseClientMode(use)).refineToOrDie[Exception]

  final def setNeedClientAuth(use: Boolean) = IO.effect(engine.setNeedClientAuth(use)).refineToOrDie[Exception]

  final def getDelegatedTask() = effectBlocking {
    var task: Runnable = null
    do {
      task = engine.getDelegatedTask()
      if (task != null) task.run()

    } while (task != null)
  }

  final def getHandshakeStatus(): IO[Exception, SSLEngineResult.HandshakeStatus] =
    IO.effect(engine.getHandshakeStatus()).refineToOrDie[Exception]

}
