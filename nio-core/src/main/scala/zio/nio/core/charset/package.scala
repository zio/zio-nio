package zio
package nio
package core

import zio.stream.Sink

package object charset {

  def chunkCollectSink[A]: Sink[Nothing, Nothing, Chunk[A], Chunk[A]] =
    Sink.foldLeft(Chunk[A]())(_ ++ _)

}
