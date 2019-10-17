package zio.nio.channels

import javax.net.ssl.SSLContext

import zio.IO
import zio.nio.Buffer

import javax.net.ssl.{ SSLEngineResult, SSLSession }
import javax.net.ssl.SSLEngineResult.HandshakeStatus._

import zio.duration.Duration
import zio.ZManaged

import zio.{ Chunk, IO, ZIO }
import zio.blocking.Blocking

import zio.clock.Clock

import zio.nio.SSLEngine

object AsynchronousTlsByteChannel {

  def apply(
    raw_ch: AsynchronousSocketChannel,
    sslContext: SSLContext
  ): ZManaged[Blocking with Clock, Exception, AsynchronousTlsByteChannel] = {
    val open = for {
      ssl_engine <- IO.effect(new SSLEngine(sslContext.createSSLEngine()))
      _          <- ssl_engine.setUseClientMode(true)
      _          <- ssl_engine.setNeedClientAuth(false)
      _          <- AsynchronousTlsByteChannel.open(raw_ch, ssl_engine)
      r          <- IO.effect(new AsynchronousTlsByteChannel(raw_ch, ssl_engine))
    } yield (r)

    ZManaged.make(open.refineToOrDie[Exception])(_.close.orDie)

  }

  private[nio] def open(raw_ch: AsynchronousByteChannel, ssl_engine: SSLEngine) = {
    val BUFF_SZ = ssl_engine.engine.getSession().getPacketBufferSize()

    var sequential_unwrap_flag = false
    val result = for {
      in_buf  <- Buffer.byte(BUFF_SZ)
      out_buf <- Buffer.byte(BUFF_SZ)
      empty   <- Buffer.byte(0)
      _       <- ssl_engine.wrap(empty, out_buf) *> out_buf.flip *> raw_ch.writeBuffer(out_buf)
      _       <- out_buf.clear
      loop = ssl_engine.getHandshakeStatus().flatMap {
        _ match {

          case NEED_WRAP =>
            for {
              _               <- out_buf.clear
              result          <- ssl_engine.wrap(empty, out_buf)
              _               <- out_buf.flip
              _               <- IO.effect { sequential_unwrap_flag = false }
              handshakeStatus <- raw_ch.writeBuffer(out_buf) *> IO.effect(result.getHandshakeStatus)
            } yield (handshakeStatus)

          case NEED_UNWRAP => {
            if (sequential_unwrap_flag == false)
              for {
                _      <- in_buf.clear
                _      <- out_buf.clear
                _      <- raw_ch.readBuffer(in_buf)
                _      <- in_buf.flip
                _      <- IO.effect { sequential_unwrap_flag = true }
                result <- ssl_engine.unwrap(in_buf, out_buf)
              } yield (result.getHandshakeStatus())
            else
              for {
                _ <- out_buf.clear

                pos <- in_buf.position
                lim <- in_buf.limit

                hStat <- if (pos == lim)
                          IO.effect { sequential_unwrap_flag = false } *> IO.succeed(NEED_UNWRAP)
                        else
                          ssl_engine.unwrap(in_buf, out_buf).map(_.getHandshakeStatus())
              } yield (hStat)
          }

          case NEED_TASK => ssl_engine.getDelegatedTask() *> IO.succeed(NEED_TASK)

          case NOT_HANDSHAKING => IO.succeed(NOT_HANDSHAKING)

          case FINISHED => IO.succeed(FINISHED)

          case _ => IO.fail(new Exception("unknown: getHandshakeStatus() - possible SSLEngine commpatibility problem"))

        }
      }

      _ <- loop
            .repeat(zio.Schedule.doWhile(c => { c != FINISHED }))
            .refineToOrDie[Exception]

    } yield ()

    result

  }
}

class AsynchronousTlsByteChannel(private val channel: AsynchronousSocketChannel, private val sslEngine: SSLEngine) {

  val TLS_PACKET_SZ = sslEngine.engine.getSession().getPacketBufferSize()
  val APP_PACKET_SZ = sslEngine.engine.getSession().getApplicationBufferSize()

  //prealoc carryover buffer, position getting saved between calls
  val IN_J_BUFFER = java.nio.ByteBuffer.allocate(TLS_PACKET_SZ * 3) //was 2: trying 3 times for performance to grab more data ahead

  //used for Keep-Alive, if HTTPS
  var READ_TIMEOUT_MS: Long = 5000

  final def keepAlive(ms: Long) = { READ_TIMEOUT_MS = ms; this }

  def getSession: IO[Exception, SSLSession] =
    IO.effect(sslEngine.engine.getSession()).refineToOrDie[Exception]

  def read(expected_size: Int): ZIO[Any with Clock, Exception, Chunk[Byte]] = {
    val result = for {

      out <- Buffer.byte(expected_size * 2)
      in  <- IO.effectTotal(new zio.nio.ByteBuffer(IN_J_BUFFER)) //reuse carryover buffer from previous read(), buffer was compacted with compact(), only non-processed data left

      nb <- channel.readBuffer(in, Duration(READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS))

      _ <- if (nb == -1) IO.fail(new Exception("AsynchronousServerTlsByteChannel#read() with -1 "))
          else IO.unit

      _ <- in.flip

      loop = for {
        res  <- sslEngine.unwrap(in, out)
        stat <- IO.effect(res.getStatus())
        rem <- if (stat != SSLEngineResult.Status.OK) {
                if (stat == SSLEngineResult.Status.BUFFER_UNDERFLOW)
                  IO.succeed(0)
                else
                  IO.fail(new Exception("AsynchronousTlsByteChannel#read() " + res.toString()))
              } else in.remaining
      } yield (rem)
      _ <- loop.repeat(zio.Schedule.doWhile(_ != 0))
      _ <- out.flip
      //****compact, some data may be carried over for next read call
      _ <- in.compact

      limit <- out.limit
      array <- out.array

      chunk <- IO.effect(Chunk.fromArray(array).take(limit))

    } yield (chunk)

    result.refineToOrDie[Exception]
  }

  final def write(chunk: Chunk[Byte]): ZIO[Any with Clock, Exception, Int] = {
    val res = for {

      in <- Buffer.byte(chunk)

      out <- Buffer.byte(if (chunk.length > TLS_PACKET_SZ) chunk.length * 2 else TLS_PACKET_SZ * 2)
      _   <- out.clear

      loop = for {
        res  <- sslEngine.wrap(in, out)
        stat <- IO.effect(res.getStatus())
        _ <- if (stat != SSLEngineResult.Status.OK)
              IO.fail(new Exception("AsynchronousTlsByteChannel#write() " + res.toString()))
            else IO.unit
        rem <- in.remaining
      } yield (rem)
      _ <- loop.repeat(zio.Schedule.doWhile(_ != 0))
      _ <- out.flip

      nBytes <- channel.writeBuffer(out)

    } yield (nBytes)

    res.refineToOrDie[Exception]
  }

  //just a socket close on already closed/broken connection
  final def close_socket_only: IO[Nothing, Unit] =
    channel.close.catchAll(_ => IO.unit)

  //close with TLS close_notify
  final def close: ZIO[Any with Clock, Nothing, Unit] = {
    val result = for {
      _     <- IO.effect(sslEngine.engine.getSession().invalidate())
      _     <- sslEngine.closeOutbound()
      empty <- Buffer.byte(0)
      out   <- Buffer.byte(TLS_PACKET_SZ)
      loop  = sslEngine.wrap(empty, out) *> sslEngine.isOutboundDone()
      _     <- loop.repeat(zio.Schedule.doUntil((status: Boolean) => status))
      _     <- out.flip
      _     <- channel.writeBuffer(out)
      _     <- channel.close
    } yield ()

    result.catchAll(_ => IO.unit)
  }

}

object AsynchronousServerTlsByteChannel {

  def apply(
    raw_ch: AsynchronousSocketChannel,
    sslContext: SSLContext
  ): ZManaged[Blocking with Clock, Exception, AsynchronousTlsByteChannel] = {

    val open = for {
      ssl_engine <- IO.effect(new SSLEngine(sslContext.createSSLEngine()))
      _          <- ssl_engine.setUseClientMode(false)
      _          <- AsynchronousServerTlsByteChannel.open(raw_ch, ssl_engine)
      r          <- IO.effect(new AsynchronousTlsByteChannel(raw_ch, ssl_engine))
    } yield (r)

    ZManaged.make(open.refineToOrDie[Exception])(_.close.orDie)

  }

  //TLS handshake is here
  private[nio] def open(raw_ch: AsynchronousByteChannel, ssl_engine: SSLEngine) = {
    val BUFF_SZ = ssl_engine.engine.getSession().getPacketBufferSize()

    var sequential_unwrap_flag = false

    val result = for {

      in_buf  <- Buffer.byte(BUFF_SZ)
      out_buf <- Buffer.byte(BUFF_SZ)
      empty   <- Buffer.byte(0)
      _       <- raw_ch.readBuffer(in_buf) *> in_buf.flip *> ssl_engine.unwrap(in_buf, out_buf)
      loop = ssl_engine.getHandshakeStatus().flatMap {
        _ match {

          case NEED_WRAP =>
            for {
              _               <- out_buf.clear
              result          <- ssl_engine.wrap(empty, out_buf)
              _               <- out_buf.flip
              _               <- IO.effect { sequential_unwrap_flag = false }
              handshakeStatus <- raw_ch.writeBuffer(out_buf) *> IO.effect(result.getHandshakeStatus)
            } yield (handshakeStatus)

          case NEED_UNWRAP => {
            if (sequential_unwrap_flag == false)
              for {
                _      <- in_buf.clear
                _      <- out_buf.clear
                _      <- raw_ch.readBuffer(in_buf)
                _      <- in_buf.flip
                _      <- IO.effect { sequential_unwrap_flag = true }
                result <- ssl_engine.unwrap(in_buf, out_buf)
              } yield (result.getHandshakeStatus())
            else
              for {
                _ <- out_buf.clear

                pos <- in_buf.position
                lim <- in_buf.limit

                hStat <- if (pos == lim)
                          IO.effect { sequential_unwrap_flag = false } *> IO.succeed(NEED_UNWRAP)
                        else
                          ssl_engine.unwrap(in_buf, out_buf).map(_.getHandshakeStatus())
              } yield (hStat)
          }

          case NEED_TASK => ssl_engine.getDelegatedTask() *> IO.succeed(NEED_TASK)

          case NOT_HANDSHAKING => IO.succeed(NOT_HANDSHAKING)

          case FINISHED => IO.succeed(FINISHED)

          case _ => IO.fail(new Exception("unknown: getHandshakeStatus() - possible SSLEngine commpatibility problem"))

        }
      }

      r <- loop
            .repeat(zio.Schedule.doWhile(c => { c != FINISHED }))
            .refineToOrDie[Exception]

    } yield (r)
    result
  }

}
