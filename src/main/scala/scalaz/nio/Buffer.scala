package scalaz.nio

import scalaz.zio.IO

import scalaz._
//import scalaz.Scalaz._

import java.nio. {Buffer      => JBuffer}
import java.nio. {ByteBuffer  => JByteBuffer}
import scala.reflect.ClassTag
//import scala.{Array => SArray}

//import java.nio.channels.CompletionHandler

//import java.nio.channels.{AsynchronousByteChannel => JAsynchronousByteChannel}

//case class Array[A: ClassTag](private val array: SArray[A]) {
//  final def length = array.length

    // Expose all methods in IO
//}

@specialized  // See if Specialized will work on return values, e.g. `get`
abstract class Buffer[A: ClassTag] private (private val buffer: JBuffer) {
  final def capacity: Int = buffer.capacity

  // should it return Unit?
  final def clear: IO[Nothing, Buffer[A]] = IO.sync(buffer.clear().asInstanceOf[Buffer[A]])

  def get: A

  final def array: IO[Exception, Maybe[Array[A]]] = 
    IO.syncException {
      // todo: avoid explicit casting
      val a: Array[A] = buffer.array().asInstanceOf[Array[A]]

      Maybe.fromNullable(a)
    }
    
}

object Buffer {
  def byte(capacity: Int): Buffer[Byte] = ByteBuffer(capacity)
  def char(capacity: Int): Buffer[Char] = CharBuffer(capacity)

  private class ByteBuffer private(private val byteBuffer: JByteBuffer) extends Buffer[Byte](byteBuffer) {
    def get: Byte = byteBuffer.array().asInstanceOf[Byte]
  }

  private class CharBuffer private(private val charBuffer: JByteBuffer) extends Buffer[Char](charBuffer) {
    def get: Char = charBuffer.array().asInstanceOf[Char]
  }

  private object ByteBuffer {
    def apply(capacity: Int): Buffer[Byte] = new ByteBuffer(JByteBuffer.allocate(capacity))
  }

  private object CharBuffer {
    def apply(capacity: Int): Buffer[Char] = new CharBuffer(JByteBuffer.allocate(capacity))
  }

}



// class AsynchronousByteChannel private (
//     private val channel: JAsynchronousByteChannel) {
//   /**
//     *  Reads some data into the byte buffer, returning the number of bytes
//     *  actually read, or -1 if no bytes were read.
//     */
//   final def read(b: ByteBuffer): IO[Exception, Int] = 
//     IO.async0[Exception, Int] { (k: ExitResult[Exception, Int] => Unit) =>
//       try {
//         channel.read(b.byteBuffer, (), new CompletionHandler[Int, Unit] {
//           def completed(result: Int, u: Unit): Unit = 
//             k(ExitResult.Completed(result))

//           def failed(t: Throwable, u: Unit): Unit = 
//             t match {
//               case e : Exception => k(ExitResult.Failed(e))
//               case _ => k(ExitResult.Terminated(t))
//             }
//         })

//         Async.later[Exception, Int]
//       } catch {
//         case e : Exception => Async.now(ExitResult.Failed(e))
//         case t : Throwable => Async.now(ExitResult.Terminated(t))
//       }
//     }

//   final def write(b: ByteBuffer): IO[Exception, Int] = ???
// }
