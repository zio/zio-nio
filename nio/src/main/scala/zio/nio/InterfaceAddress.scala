package zio.nio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.net.{InterfaceAddress => JInterfaceAddress}

final class InterfaceAddress private[nio] (private val jInterfaceAddress: JInterfaceAddress) {
  def address: InetAddress = new InetAddress(jInterfaceAddress.getAddress)

  def broadcast: InetAddress = new InetAddress(jInterfaceAddress.getBroadcast)

  def networkPrefixLength: Short = jInterfaceAddress.getNetworkPrefixLength
}
