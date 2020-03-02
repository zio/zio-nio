package zio.nio.core.charset

sealed abstract private[charset] class StreamCodeState

private[charset] object StreamCodeState {

  case object Pull extends StreamCodeState

  case object EndOfInput extends StreamCodeState

  case object Flush extends StreamCodeState

  case object Done extends StreamCodeState

}
