package zio.nio.core

import java.net.{ InetSocketAddress => JInetSocketAddress, SocketAddress => JSocketAddress }

import zio.UIO

/**
 * Representation of a socket address without a specific protocol.
 *
 * The concrete subclass [[InetSocketAddress]] is used in practice.
 */
sealed class SocketAddress private[nio] (private[nio] val jSocketAddress: JSocketAddress) {

  final override def equals(obj: Any): Boolean =
    obj match {
      case other: SocketAddress => other.jSocketAddress == this.jSocketAddress
      case _                    => false
    }

  final override def hashCode(): Int = jSocketAddress.hashCode()

  final override def toString: String = jSocketAddress.toString
}

object SocketAddress {

  def fromJava(jSocketAddress: JSocketAddress): SocketAddress =
    jSocketAddress match {
      case inet: JInetSocketAddress =>
        new InetSocketAddress(inet)
      case other                    =>
        new SocketAddress(other)
    }

}

/**
 * Representation of an IP Socket Address (IP address + port number).
 *
 * It can also be a pair (hostname + port number), in which case an attempt
 * will be made to resolve the hostname.
 * If resolution fails then the address is said to be unresolved but can still
 * be used on some circumstances like connecting through a proxy.
 * It provides an immutable object used by sockets for binding, connecting,
 * or as returned values.
 *
 * The wildcard is a special local IP address. It usually means "any" and can
 * only be used for bind operations.
 */
final class InetSocketAddress private[nio] (private val jInetSocketAddress: JInetSocketAddress)
    extends SocketAddress(jInetSocketAddress) {

  /**
   * The socket's address.
   *
   * @return The address of the socket, or `None` if this socket address is not resolved.
   */
  def address: Option[InetAddress] = Option(jInetSocketAddress.getAddress).map(new InetAddress(_))

  /**
   * The socket's port number.
   */
  def port: Int = jInetSocketAddress.getPort

  /**
   * Gets the hostname.
   *
   * Note: This method may trigger a name service reverse lookup if the
   * address was created with a literal IP address.
   */
  def hostName: UIO[String] = UIO.effectTotal(jInetSocketAddress.getHostName)

  /**
   * Returns the hostname, or the String form of the address if it doesn't
   * have a hostname (it was created using a literal).
   *
   * This has the benefit of not attempting a reverse lookup.
   * This is an effect because the result could change if a reverse lookup
   * is performed, for example by calling `hostName`.
   */
  def hostString: UIO[String] = UIO.effectTotal(jInetSocketAddress.getHostString)

  /**
   * Checks whether the address has been resolved or not.
   */
  def isUnresolved: Boolean = jInetSocketAddress.isUnresolved

}

object InetSocketAddress {

  /**
   * Creates a socket address where the IP address is the wildcard address and the port number a specified value.
   *
   * The socket address will be ''resolved''.
   */
  def wildCard(port: Int): UIO[InetSocketAddress] = UIO.effectTotal(new InetSocketAddress(new JInetSocketAddress(port)))

  def wildCardEphemeral: UIO[InetSocketAddress] = wildCard(0)

  /**
   * Creates a socket address from an IP address and a port number.
   *
   * The socket address will be ''resolved''.
   */
  def hostname(hostname: String, port: Int): UIO[InetSocketAddress] =
    UIO.effectTotal(new InetSocketAddress(new JInetSocketAddress(hostname, port)))

  def hostnameEphemeral(hostname: String): UIO[InetSocketAddress] = this.hostname(hostname, 0)

  /**
   * Creates a socket address from a hostname and a port number.
   *
   * An attempt will be made to resolve the hostname into an `InetAddress`.
   * If that attempt fails, the socket address will be flagged as ''unresolved''.
   */
  def inetAddress(address: InetAddress, port: Int): UIO[InetSocketAddress] =
    UIO.effectTotal(new InetSocketAddress(new JInetSocketAddress(address.jInetAddress, port)))

  def inetAddressEphemeral(address: InetAddress): UIO[InetSocketAddress] = inetAddress(address, 0)

  /**
   * Creates an unresolved socket address from a hostname and a port number.
   *
   * No attempt will be made to resolve the hostname into an `InetAddress`.
   * The socket address will be flagged as ''unresolved''.
   */
  def unresolvedHostname(hostname: String, port: Int): UIO[InetSocketAddress] =
    UIO.effectTotal(new InetSocketAddress(JInetSocketAddress.createUnresolved(hostname, port)))

  def unresolvedHostnameEphemeral(hostname: String): UIO[InetSocketAddress] = unresolvedHostname(hostname, 0)

}
