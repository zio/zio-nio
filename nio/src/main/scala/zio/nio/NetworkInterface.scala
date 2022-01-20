package zio.nio

import com.github.ghik.silencer.silent
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{IO, ZTraceElement}

import java.net.{NetworkInterface => JNetworkInterface, SocketException}
import scala.collection.JavaConverters._

class NetworkInterface private[nio] (private[nio] val jNetworkInterface: JNetworkInterface) {

  def name: String = jNetworkInterface.getName

  @silent
  def inetAddresses: List[InetAddress] = jNetworkInterface.getInetAddresses.asScala.map(new InetAddress(_)).toList

  @silent
  def interfaceAddresses: List[InterfaceAddress] =
    jNetworkInterface.getInterfaceAddresses.asScala.map(new InterfaceAddress(_)).toList

  @silent
  def subInterfaces: Iterator[NetworkInterface] =
    jNetworkInterface.getSubInterfaces.asScala.map(new NetworkInterface(_))

  def parent: NetworkInterface = new NetworkInterface(jNetworkInterface.getParent)

  def index: Int = jNetworkInterface.getIndex

  def displayName: String = jNetworkInterface.getDisplayName

  def isUp(implicit trace: ZTraceElement): IO[SocketException, Boolean] =
    IO.attempt(jNetworkInterface.isUp).refineToOrDie[SocketException]

  def isLoopback(implicit trace: ZTraceElement): IO[SocketException, Boolean] =
    IO.attempt(jNetworkInterface.isLoopback).refineToOrDie[SocketException]

  def isPointToPoint(implicit trace: ZTraceElement): IO[SocketException, Boolean] =
    IO.attempt(jNetworkInterface.isPointToPoint).refineToOrDie[SocketException]

  def supportsMulticast(implicit trace: ZTraceElement): IO[SocketException, Boolean] =
    IO.attempt(jNetworkInterface.supportsMulticast).refineToOrDie[SocketException]

  def hardwareAddress(implicit trace: ZTraceElement): IO[SocketException, Array[Byte]] =
    IO.attempt(jNetworkInterface.getHardwareAddress).refineToOrDie[SocketException]

  def mtu(implicit trace: ZTraceElement): IO[SocketException, Int] =
    IO.attempt(jNetworkInterface.getMTU).refineToOrDie[SocketException]

  def isVirtual: Boolean = jNetworkInterface.isVirtual
}

object NetworkInterface {

  def byName(name: String)(implicit trace: ZTraceElement): IO[SocketException, NetworkInterface] =
    IO.attempt(JNetworkInterface.getByName(name))
      .refineToOrDie[SocketException]
      .map(new NetworkInterface(_))

  def byIndex(index: Integer)(implicit trace: ZTraceElement): IO[SocketException, NetworkInterface] =
    IO.attempt(JNetworkInterface.getByIndex(index))
      .refineToOrDie[SocketException]
      .map(new NetworkInterface(_))

  def byInetAddress(address: InetAddress)(implicit trace: ZTraceElement): IO[SocketException, NetworkInterface] =
    IO.attempt(JNetworkInterface.getByInetAddress(address.jInetAddress))
      .refineToOrDie[SocketException]
      .map(new NetworkInterface(_))

  @silent
  def networkInterfaces(implicit trace: ZTraceElement): IO[SocketException, Iterator[NetworkInterface]] =
    IO.attempt(JNetworkInterface.getNetworkInterfaces.asScala)
      .refineToOrDie[SocketException]
      .map(_.map(new NetworkInterface(_)))

}
