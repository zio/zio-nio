package zio.nio

package object core {

  implicit final class RichInt(val value: Int) extends AnyVal {

    /**
     * Handle -1 magic number returned by many Java APIs when end of file is reached.
     *
     * @return None for `value` < 0, otherwise `Some(value)`
     */
    def eofCheck: Option[Int] = if (value < 0L) None else Some(value)

  }

  implicit final class RichLong(val value: Long) extends AnyVal {

    /**
     * Handle -1 magic number returned by many Java APIs when end of file is reached.
     *
     * @return None for `value` < 0, otherwise `Some(value)`
     */
    def eofCheck: Option[Long] = if (value < 0L) None else Some(value)

  }

}
