package scalaz.nio

import java.net.{ InetSocketAddress => JInetSocketAddress, SocketAddress => JSocketAddress }

import scalaz.zio.{ IO, JustExceptions }

class SocketAddress private[nio] (private[nio] val jSocketAddress: JSocketAddress)

class InetSocketAddress private[nio] (private val jInetSocketAddress: JInetSocketAddress)
    extends SocketAddress(jInetSocketAddress) {

  def port: Int = jInetSocketAddress.getPort

  def hostName: IO[Exception, String] =
    IO.effect(jInetSocketAddress.getHostName).refineOrDie(JustExceptions)

  def hostString: String = jInetSocketAddress.getHostString

  def isUnresolved: Boolean = jInetSocketAddress.isUnresolved
}

object SocketAddress {

  def inetSocketAddress(port: Int): IO[Exception, InetSocketAddress] = InetSocketAddress(port)

  def inetSocketAddress(hostname: String, port: Int): IO[Exception, InetSocketAddress] =
    InetSocketAddress(hostname, port)

  def inetSocketAddress(address: InetAddress, port: Int): IO[Exception, InetSocketAddress] =
    InetSocketAddress(address, port)

  def unresolvedInetSocketAddress(hostname: String, port: Int): IO[Exception, InetSocketAddress] =
    InetSocketAddress.createUnresolved(hostname, port)

  private object InetSocketAddress {

    def apply(port: Int): IO[Exception, InetSocketAddress] =
      IO.effect(new JInetSocketAddress(port))
        .refineOrDie(JustExceptions)
        .map(new InetSocketAddress(_))

    def apply(host: String, port: Int): IO[Exception, InetSocketAddress] =
      IO.effect(new JInetSocketAddress(host, port))
        .refineOrDie(JustExceptions)
        .map(new InetSocketAddress(_))

    def apply(addr: InetAddress, port: Int): IO[Exception, InetSocketAddress] =
      IO.effect(new JInetSocketAddress(addr.jInetAddress, port))
        .refineOrDie(JustExceptions)
        .map(new InetSocketAddress(_))

    def createUnresolved(host: String, port: Int): IO[Exception, InetSocketAddress] =
      IO.effect(JInetSocketAddress.createUnresolved(host, port))
        .refineOrDie(JustExceptions)
        .map(new InetSocketAddress(_))
  }
}
