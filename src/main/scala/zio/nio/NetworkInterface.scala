package zio.nio

import java.net.{ SocketException, NetworkInterface => JNetworkInterface }

import com.github.ghik.silencer.silent
import zio.IO

import scala.collection.JavaConverters._

class NetworkInterface private[nio] (private[nio] val jNetworkInterface: JNetworkInterface) {
  import NetworkInterface.JustSocketException

  def name: String = jNetworkInterface.getName

  @silent
  def inetAddresses: Iterator[InetAddress] =
    jNetworkInterface.getInetAddresses.asScala.map(new InetAddress(_))

  @silent
  def interfaceAddresses: List[InterfaceAddress] =
    jNetworkInterface.getInterfaceAddresses.asScala.map(new InterfaceAddress(_)).toList

  @silent
  def subInterfaces: Iterator[NetworkInterface] =
    jNetworkInterface.getSubInterfaces.asScala.map(new NetworkInterface(_))

  def parent: NetworkInterface = new NetworkInterface(jNetworkInterface.getParent)

  def index: Int = jNetworkInterface.getIndex

  def displayName: String = jNetworkInterface.getDisplayName

  val isUp: IO[SocketException, Boolean] =
    IO.effect(jNetworkInterface.isUp).refineOrDie(JustSocketException)

  val isLoopback: IO[SocketException, Boolean] =
    IO.effect(jNetworkInterface.isLoopback).refineOrDie(JustSocketException)

  val isPointToPoint: IO[SocketException, Boolean] =
    IO.effect(jNetworkInterface.isPointToPoint).refineOrDie(JustSocketException)

  val supportsMulticast: IO[SocketException, Boolean] =
    IO.effect(jNetworkInterface.supportsMulticast).refineOrDie(JustSocketException)

  val hardwareAddress: IO[SocketException, Array[Byte]] =
    IO.effect(jNetworkInterface.getHardwareAddress).refineOrDie(JustSocketException)

  val mtu: IO[SocketException, Int] =
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

  @silent
  def networkInterfaces: IO[SocketException, Iterator[NetworkInterface]] =
    IO.effect(JNetworkInterface.getNetworkInterfaces.asScala)
      .refineOrDie(JustSocketException)
      .map(_.map(new NetworkInterface(_)))
}
