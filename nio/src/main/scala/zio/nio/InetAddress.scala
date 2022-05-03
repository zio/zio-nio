package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, IO, Trace, ZIO}

import java.io.IOException
import java.net.{InetAddress => JInetAddress, UnknownHostException}

/**
 * Representation of an Internet Protocol (IP) address.
 *
 * Will be either a 32-bit IPv4 address or a 128-bit IPv6 address.
 */
final class InetAddress private[nio] (private[nio] val jInetAddress: JInetAddress) {
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

  def isReachable(timeOut: Int)(implicit trace: Trace): IO[IOException, Boolean] =
    ZIO.attempt(jInetAddress.isReachable(timeOut)).refineToOrDie[IOException]

  def isReachable(
    networkInterface: NetworkInterface,
    ttl: Integer,
    timeout: Integer
  )(implicit trace: Trace): IO[IOException, Boolean] =
    ZIO
      .attempt(jInetAddress.isReachable(networkInterface.jNetworkInterface, ttl, timeout))
      .refineToOrDie[IOException]

  def hostName: String = jInetAddress.getHostName

  def canonicalHostName: String = jInetAddress.getCanonicalHostName

  def address: Chunk[Byte] = Chunk.fromArray(jInetAddress.getAddress)

  override def hashCode(): Int = jInetAddress.hashCode()

  override def equals(obj: Any): Boolean =
    obj match {
      case other: InetAddress => jInetAddress.equals(other.jInetAddress)
      case _                  => false
    }

  override def toString: String = jInetAddress.toString

}

object InetAddress {

  def byAddress(bytes: Chunk[Byte])(implicit trace: Trace): IO[UnknownHostException, InetAddress] =
    ZIO
      .attempt(new InetAddress(JInetAddress.getByAddress(bytes.toArray)))
      .refineToOrDie[UnknownHostException]

  def byAddress(hostname: String, bytes: Chunk[Byte])(implicit
    trace: Trace
  ): IO[UnknownHostException, InetAddress] =
    ZIO
      .attempt(new InetAddress(JInetAddress.getByAddress(hostname, bytes.toArray)))
      .refineToOrDie[UnknownHostException]

  def byAllName(hostName: String)(implicit trace: Trace): IO[UnknownHostException, List[InetAddress]] =
    ZIO
      .attempt(JInetAddress.getAllByName(hostName).toList.map(new InetAddress(_)))
      .refineToOrDie[UnknownHostException]

  def byName(hostName: String)(implicit trace: Trace): IO[UnknownHostException, InetAddress] =
    ZIO
      .attempt(new InetAddress(JInetAddress.getByName(hostName)))
      .refineToOrDie[UnknownHostException]

  def localHost(implicit trace: Trace): IO[UnknownHostException, InetAddress] =
    ZIO.attempt(new InetAddress(JInetAddress.getLocalHost)).refineToOrDie[UnknownHostException]

}
