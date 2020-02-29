package zio.nio.core.charset

sealed abstract class AutoDetect

object AutoDetect {

  case object NotSupported extends AutoDetect

  case object NotDetected extends AutoDetect

  final case class Detected(charset: Charset) extends AutoDetect

}
