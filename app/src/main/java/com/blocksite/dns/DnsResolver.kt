package com.blocksite.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val UPSTREAM_DNS = "8.8.8.8"
private const val DNS_PORT = 53
private const val TIMEOUT_MS = 3000

fun forwardDnsQuery(query: ByteArray): ByteArray? {
    return try {
        val socket = DatagramSocket()
        socket.soTimeout = TIMEOUT_MS

        val address = InetAddress.getByName(UPSTREAM_DNS)
        val sendPacket = DatagramPacket(query, query.size, address, DNS_PORT)
        socket.send(sendPacket)

        val buffer = ByteArray(4096)
        val receivePacket = DatagramPacket(buffer, buffer.size)
        socket.receive(receivePacket)
        socket.close()

        buffer.copyOf(receivePacket.length)
    } catch (e: Exception) {
        null
    }
}
