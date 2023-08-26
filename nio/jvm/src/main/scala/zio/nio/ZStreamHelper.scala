package zio.nio

import zio.stream.ZStream
import zio.{Scope, ZIO, Trace}

/**
 * A mutable buffer of shorts.
 */
object ZStreamHelper {

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStream[A](stream: => java.util.stream.Stream[A])(implicit
    trace: Trace
  ): ZStream[Any, Throwable, A] =
    fromJavaStream(stream, ZStream.DefaultChunkSize)

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStream[A](
    stream: => java.util.stream.Stream[A],
    chunkSize: Int
  )(implicit trace: Trace): ZStream[Any, Throwable, A] =
    ZStream.fromJavaIteratorScoped(
      ZIO.acquireRelease(ZIO.attempt(stream))(stream => ZIO.succeed(stream.close())).map(_.iterator()),
      chunkSize
    )

  /**
   * Creates a stream from a scoped Java stream
   */
  final def fromJavaStreamScoped[R, A](stream: => ZIO[Scope with R, Throwable, java.util.stream.Stream[A]])(implicit
    trace: Trace
  ): ZStream[R, Throwable, A] =
    fromJavaStreamScoped[R, A](stream, ZStream.DefaultChunkSize)

  /**
   * Creates a stream from a scoped Java stream
   */
  final def fromJavaStreamScoped[R, A](
    stream: => ZIO[Scope with R, Throwable, java.util.stream.Stream[A]],
    chunkSize: Int
  )(implicit trace: Trace): ZStream[R, Throwable, A] =
    ZStream.scoped[R](stream).flatMap(fromJavaStream(_, chunkSize))
}
