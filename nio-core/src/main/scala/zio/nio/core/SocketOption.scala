package zio.nio.core

import java.net.{ SocketOption => JSocketOption }

class SocketOption[T] private[nio] (private[nio] val jSocketOption: JSocketOption[T])
