package zio.nio.core

import java.net.{ InterfaceAddress => JinterfaceAddress }

class InterfaceAddress private[nio] (private val jInterfaceAddress: JinterfaceAddress) {
  def address: InetAddress = new InetAddress(jInterfaceAddress.getAddress)

  def broadcast: InetAddress = new InetAddress(jInterfaceAddress.getBroadcast)

  def networkPrefixLength: Short = jInterfaceAddress.getNetworkPrefixLength
}
