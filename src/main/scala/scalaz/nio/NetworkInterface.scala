package scalaz.nio

import java.net.{ NetworkInterface => JNetworkInterface, SocketException }

import scalaz.IList
import scalaz.zio.IO

import scala.collection.JavaConverters._

class NetworkInterface private[nio] (private[nio] val jNetworkInterface: JNetworkInterface) {

  import NetworkInterface.JustSocketException

  def name: String = jNetworkInterface.getName

  def inetAddresses: Iterator[InetAddress] =
    jNetworkInterface.getInetAddresses.asScala.map(new InetAddress(_))

  def interfaceAddresses: IList[InterfaceAddress] =
    IList.fromList(
      jNetworkInterface.getInterfaceAddresses.asScala.map(new InterfaceAddress(_)).toList
    )

  def subInterfaces: Iterator[NetworkInterface] =
    jNetworkInterface.getSubInterfaces.asScala.map(new NetworkInterface(_))

  def parent: NetworkInterface = new NetworkInterface(jNetworkInterface.getParent)

  def index: Int = jNetworkInterface.getIndex

  def displayName: String = jNetworkInterface.getDisplayName

  def isUp: IO[SocketException, Boolean] =
    IO.effect(jNetworkInterface.isUp).refineOrDie(JustSocketException)

  def isLoopback: IO[SocketException, Boolean] =
    IO.effect(jNetworkInterface.isLoopback).refineOrDie(JustSocketException)

  def isPointToPoint: IO[SocketException, Boolean] =
    IO.effect(jNetworkInterface.isPointToPoint).refineOrDie(JustSocketException)

  def supportsMulticast: IO[SocketException, Boolean] =
    IO.effect(jNetworkInterface.supportsMulticast).refineOrDie(JustSocketException)

  def hardwareAddress: IO[SocketException, Array[Byte]] =
    IO.effect(jNetworkInterface.getHardwareAddress).refineOrDie(JustSocketException)

  def mtu: IO[SocketException, Int] =
    IO.effect(jNetworkInterface.getMTU).refineOrDie(JustSocketException)

  def isVirtual: Boolean = jNetworkInterface.isVirtual

}

object NetworkInterface {

  val JustSocketException: PartialFunction[Throwable, SocketException] = {
    case e: SocketException => e
  }

  def byName(name: String): IO[SocketException, NetworkInterface] =
    IO.effect(JNetworkInterface.getByName(name))
      .refineOrDie(JustSocketException)
      .map(new NetworkInterface(_))

  def byIndex(index: Integer): IO[SocketException, NetworkInterface] =
    IO.effect(JNetworkInterface.getByIndex(index))
      .refineOrDie(JustSocketException)
      .map(new NetworkInterface(_))

  def byInetAddress(address: InetAddress): IO[SocketException, NetworkInterface] =
    IO.effect(JNetworkInterface.getByInetAddress(address.jInetAddress))
      .refineOrDie(JustSocketException)
      .map(new NetworkInterface(_))

  def networkInterfaces: IO[SocketException, Iterator[NetworkInterface]] =
    IO.effect(JNetworkInterface.getNetworkInterfaces.asScala)
      .refineOrDie(JustSocketException)
      .map(_.map(new NetworkInterface(_)))
}
