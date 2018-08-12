package scalaz.nio

import scalaz.zio.IO

import scalaz._
import scalaz.Scalaz._

import java.nio. {Buffer      => JBuffer}
import java.nio. {ByteBuffer  => JByteBuffer}
import java.lang.{Array       => JArray}

import java.nio.channels.CompletionHandler

import java.nio.channels.{AsynchronousByteChannel => JAsynchronousByteChannel}

case class Array[A: ClassTag](private val array: Array[A]) {
  final def length = array.length 

  // Expose all methods in IO
}

@specialized  // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private (private val buffer: JBuffer) {
  final def capacity: Int = buffer.capacity 

  final def clear: IO[Nothing, Unit] = IO.sync(buffer.clear())

  final def get: A

  final def array: Maybe[Array[A]] = 
    try {
      val a = buffer.array()

      if (a neq null) Maybe.just(Array(a)) else Maybe.empty
    } catch {
      case _ => Maybe.empty
    }
}
object Buffer {
  def byte(capacity: Int): Buffer[Byte] = new ByteBuffer(buffer)
  def char(capacity: Int): Buffer[Char] = ???
}

private class ByteBuffer private (private val byteBuffer: JByteBuffer) 
  extends Buffer[Byte](byteBuffer) {
}

class AsynchronousByteChannel private (
    private val channel: JAsynchronousByteChannel) {
  /**
    *  Reads some data into the byte buffer, returning the number of bytes
    *  actually read, or -1 if no bytes were read.
    */
  final def read(b: ByteBuffer): IO[Exception, Int] = 
    IO.async0[Exception, Int] { (k: ExitResult[Exception, Int] => Unit) =>
      try {
        channel.read(b.byteBuffer, (), new CompletionHandler[Int, Unit] {
          def completed(result: Int, u: Unit): Unit = 
            k(ExitResult.Completed(result))

          def failed(t: Throwable, u: Unit): Unit = 
            t match {
              case e : Exception => k(ExitResult.Failed(e))
              case _ => k(ExitResult.Terminated(t))
            }
        })

        Async.later[Exception, Int]
      } catch {
        case e : Exception => Async.now(ExitResult.Failed(e))
        case t : Throwable => Async.now(ExitResult.Terminated(t))
      }
    }

  final def write(b: ByteBuffer): IO[Exception, Int] = ???
}
