## Client + Server

```tut
object T {
  import scalaz.zio.{ App, IO }

  object ClientServer extends App {

    override def run(args: List[String]): IO[Nothing, ClientServer.ExitStatus] =
      IO.now(()).const(ExitStatus.ExitNow(0))

  }
}
```