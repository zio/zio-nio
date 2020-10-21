package zio
package nio
package core
package charset

import java.nio.{ charset => j }

sealed trait CoderResult {

  import CoderResult._

  def isError: Boolean =
    this match {
      case Underflow | Overflow => false
      case _                    => true
    }

}

object CoderResult {

  case object Underflow extends CoderResult

  case object Overflow extends CoderResult

  final case class Unmappable(length: Int) extends CoderResult

  final case class Malformed(length: Int) extends CoderResult

  def fromJava(javaResult: j.CoderResult): CoderResult =
    javaResult match {
      case r if r.isOverflow()   => Overflow
      case r if r.isUnderflow()  => Underflow
      case r if r.isUnmappable() => Unmappable(r.length())
      case r if r.isMalformed()  => Malformed(r.length())
      case r                     => sys.error(s"CoderResult in bad state: $r")
    }

}
