package scalaz.nio

import java.net.{ InetAddress => JInetAddress }

import scalaz.zio.IO

class InetAddress private[nio] (private[nio] val jInetAddress: JInetAddress) {

  def isMulticastAddress: Boolean = jInetAddress.isMulticastAddress

  def isAnyLocalAddress: Boolean = jInetAddress.isAnyLocalAddress

  def isLoopbackAddress: Boolean = jInetAddress.isLoopbackAddress

  def isLinkLocalAddress: Boolean = jInetAddress.isLinkLocalAddress

  def isSiteLocalAddress: Boolean = jInetAddress.isSiteLocalAddress

  def isMCGlobal: Boolean = jInetAddress.isMCGlobal

  def isMCNodeLocal: Boolean = jInetAddress.isMCNodeLocal

  def isMCLinkLocal: Boolean = jInetAddress.isMCLinkLocal

  def isMCSiteLocal: Boolean = jInetAddress.isMCSiteLocal

  def isMCOrgLocal: Boolean = jInetAddress.isMCOrgLocal

  def isReachable(timeOut: Int): IO[Exception, Boolean] =
    IO.syncException(jInetAddress.isReachable(timeOut))

  def isReachable(
    networkInterface: NetworkInterface,
    ttl: Integer,
    timeout: Integer
  ): IO[Exception, Boolean] =
    IO.syncException(jInetAddress.isReachable(networkInterface.jNetworkInterface, ttl, timeout))

  def hostname: String = jInetAddress.getHostName

  def canonicalHostName: String = jInetAddress.getCanonicalHostName

  def address: Array[Byte] = jInetAddress.getAddress

}

object InetAddress {

  def byAddress(bytes: Array[Byte]): IO[Exception, InetAddress] =
    IO.syncException(JInetAddress.getByAddress(bytes))
      .map(new InetAddress(_))

  def byAddress(hostname: String, bytes: Array[Byte]): IO[Exception, InetAddress] =
    IO.syncException(JInetAddress.getByAddress(hostname, bytes))
      .map(new InetAddress(_))

  def byAllName(hostName: String): IO[Exception, Array[InetAddress]] =
    IO.syncException(JInetAddress.getAllByName(hostName))
      .map(_.map(new InetAddress(_)))

  def byName(hostName: String): IO[Exception, InetAddress] =
    IO.syncException(JInetAddress.getByName(hostName))
      .map(new InetAddress(_))

  def localHost: IO[Exception, InetAddress] =
    IO.syncException(JInetAddress.getLocalHost).map(new InetAddress(_))
}
