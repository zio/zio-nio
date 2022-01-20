package zio.nio.charset
import zio.stacktracer.TracingImplicits.disableAutoTrace
sealed abstract class AutoDetect

object AutoDetect {

  case object NotSupported extends AutoDetect

  case object NotDetected extends AutoDetect

  final case class Detected(charset: Charset) extends AutoDetect

}
