package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, Trace, UIO, ZIO}

import java.net.{InetSocketAddress => JInetSocketAddress, SocketAddress => JSocketAddress, UnknownHostException}

/**
 * Representation of a socket address without a specific protocol.
 *
 * The concrete subclass [[InetSocketAddress]] is used in practice.
 */
sealed class SocketAddress protected (private[nio] val jSocketAddress: JSocketAddress) {

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
      case other =>
        new SocketAddress(other)
    }

}

/**
 * Representation of an IP Socket Address (IP address + port number).
 *
 * It can also be a pair (hostname + port number), in which case an attempt will be made to resolve the hostname. If
 * resolution fails then the address is said to be unresolved but can still be used on some circumstances like
 * connecting through a proxy. However, note that network channels generally do ''not'' accept unresolved socket
 * addresses.
 *
 * This class provides an immutable object used by sockets for binding, connecting, or as returned values.
 *
 * The wildcard is a special local IP address. It usually means "any" and can only be used for bind operations.
 */
final class InetSocketAddress private[nio] (private val jInetSocketAddress: JInetSocketAddress)
    extends SocketAddress(jInetSocketAddress) {

  /**
   * The socket's address.
   *
   * @return
   *   The address of the socket, or `None` if this socket address is not resolved.
   */
  def address: Option[InetAddress] = Option(jInetSocketAddress.getAddress).map(new InetAddress(_))

  /**
   * The socket's port number.
   */
  def port: Int = jInetSocketAddress.getPort

  /**
   * Gets the hostname.
   *
   * Note: This method may trigger a name service reverse lookup if the address was created with a literal IP address.
   */
  def hostName(implicit trace: Trace): UIO[String] = ZIO.succeed(jInetSocketAddress.getHostName)

  /**
   * Returns the hostname, or the String form of the address if it doesn't have a hostname (it was created using a
   * literal).
   *
   * This has the benefit of not attempting a reverse lookup. This is an effect because the result could change if a
   * reverse lookup is performed, for example by calling `hostName`.
   */
  def hostString(implicit trace: Trace): UIO[String] = ZIO.succeed(jInetSocketAddress.getHostString)

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
  def wildCard(port: Int)(implicit trace: Trace): UIO[InetSocketAddress] =
    ZIO.succeed(new InetSocketAddress(new JInetSocketAddress(port)))

  /**
   * Creates a socket address where the IP address is the wildcard address and the port is ephemeral.
   *
   * The socket address will be ''resolved''.
   */
  def wildCardEphemeral(implicit trace: Trace): UIO[InetSocketAddress] = wildCard(0)

  /**
   * Creates a socket address from a hostname and a port number.
   *
   * This method will attempt to resolve the hostname; if this fails, the returned socket address will be
   * ''unresolved''.
   */
  def hostName(hostName: String, port: Int)(implicit trace: Trace): UIO[InetSocketAddress] =
    ZIO.succeed(new InetSocketAddress(new JInetSocketAddress(hostName, port)))

  /**
   * Creates a resolved socket address from a hostname and port number.
   *
   * If the hostname cannot be resolved, fails with `UnknownHostException`.
   */
  def hostNameResolved(hostName: String, port: Int)(implicit
    trace: Trace
  ): IO[UnknownHostException, InetSocketAddress] =
    InetAddress.byName(hostName).flatMap(inetAddress(_, port))

  /**
   * Creates a socket address from a hostname, with an ephemeral port.
   *
   * This method will attempt to resolve the hostname; if this fails, the returned socket address will be
   * ''unresolved''.
   */
  def hostNameEphemeral(hostName: String)(implicit trace: Trace): UIO[InetSocketAddress] =
    this.hostName(hostName, 0)

  /**
   * Creates a resolved socket address from a hostname, with an ephemeral port.
   *
   * If the hostname cannot be resolved, fails with `UnknownHostException`.
   */
  def hostNameEphemeralResolved(hostName: String)(implicit
    trace: Trace
  ): IO[UnknownHostException, InetSocketAddress] =
    InetAddress.byName(hostName).flatMap(inetAddressEphemeral)

  /**
   * Creates a socket address from an IP address and a port number.
   */
  def inetAddress(address: InetAddress, port: Int)(implicit trace: Trace): UIO[InetSocketAddress] =
    ZIO.succeed(new InetSocketAddress(new JInetSocketAddress(address.jInetAddress, port)))

  /**
   * Creates a socket address from an IP address, with an ephemeral port.
   */
  def inetAddressEphemeral(address: InetAddress)(implicit trace: Trace): UIO[InetSocketAddress] =
    inetAddress(address, 0)

  /**
   * Creates a socket address for localhost using the specified port.
   */
  def localHost(port: Int)(implicit trace: Trace): IO[UnknownHostException, InetSocketAddress] =
    InetAddress.localHost.flatMap(inetAddress(_, port))

  /**
   * Creates an unresolved socket address from a hostname and a port number.
   *
   * No attempt will be made to resolve the hostname into an `InetAddress`. The socket address will be flagged as
   * ''unresolved''.
   */
  def unresolvedHostName(hostName: String, port: Int)(implicit trace: Trace): UIO[InetSocketAddress] =
    ZIO.succeed(new InetSocketAddress(JInetSocketAddress.createUnresolved(hostName, port)))

  /**
   * Creates an unresolved socket address from a hostname using an ephemeral port.
   *
   * No attempt will be made to resolve the hostname into an `InetAddress`. The socket address will be flagged as
   * ''unresolved''.
   */
  def unresolvedHostNameEphemeral(hostName: String)(implicit trace: Trace): UIO[InetSocketAddress] =
    unresolvedHostName(hostName, 0)

}
