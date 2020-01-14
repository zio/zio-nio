package zio.nio

package object core {

  implicit final class RichLong(val value: Long) extends AnyVal {

    /**
     * Handle -1 magic number returned by many Java APIs when end of file is reached.
     *
     * @return None for `readCount` < 0, otherwise `Some(readCount)`
     */
    def eofCheck: Option[Long] = if (value < 0L) None else Some(value)
  }
}
