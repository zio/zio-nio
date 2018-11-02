package scalaz.nio

import java.net.{ NetworkInterface => JNetworkInterface }

import scalaz.IList
import scalaz.zio.IO

import scala.collection.JavaConverters._

class NetworkInterface private[nio] (private[nio] val jNetworkInterface: JNetworkInterface) {

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

  def isUp: IO[Exception, Boolean] = IO.syncException(jNetworkInterface.isUp)

  def isLoopback: IO[Exception, Boolean] = IO.syncException(jNetworkInterface.isLoopback)

  def isPointToPoint: IO[Exception, Boolean] = IO.syncException(jNetworkInterface.isPointToPoint)

  def supportsMulticast: IO[Exception, Boolean] =
    IO.syncException(jNetworkInterface.supportsMulticast)

  def hardwareAddress: IO[Exception, Array[Byte]] =
    IO.syncException(jNetworkInterface.getHardwareAddress)

  def mtu: IO[Exception, Int] = IO.syncException(jNetworkInterface.getMTU)

  def isVirtual: Boolean = jNetworkInterface.isVirtual

}

object NetworkInterface {

  def byName(name: String): IO[Exception, NetworkInterface] =
    IO.syncException(JNetworkInterface.getByName(name)).map(new NetworkInterface(_))

  def byIndex(index: Integer): IO[Exception, NetworkInterface] =
    IO.syncException(JNetworkInterface.getByIndex(index)).map(new NetworkInterface(_))

  def byInetAddress(address: InetAddress): IO[Exception, NetworkInterface] =
    IO.syncException(JNetworkInterface.getByInetAddress(address.jInetAddress))
      .map(new NetworkInterface(_))

  def networkInterfaces: IO[Exception, Iterator[NetworkInterface]] =
    IO.syncException(JNetworkInterface.getNetworkInterfaces.asScala)
      .map(_.map(new NetworkInterface(_)))

}
