package com.blocksite

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.blocksite.data.BlocklistRepository
import com.blocksite.dns.DnsPacket
import com.blocksite.dns.forwardDnsQuery
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class BlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private lateinit var repository: BlocklistRepository

    companion object {
        const val ACTION_START = "com.blocksite.START"
        const val ACTION_STOP = "com.blocksite.STOP"
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_DNS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        repository = BlocklistRepository(applicationContext)

        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            else -> {
                startVpn()
                START_STICKY
            }
        }
    }

    private fun startVpn() {
        vpnInterface = Builder()
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_ROUTE, 0)
            .setSession("BlockSite")
            .establish()

        running = true
        Thread(::runPacketLoop, "BlockSite-VPN").start()
    }

    private fun stopVpn() {
        running = false
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    private fun runPacketLoop() {
        val vpn = vpnInterface ?: return
        val input = FileInputStream(vpn.fileDescriptor)
        val output = FileOutputStream(vpn.fileDescriptor)
        val packet = ByteArray(32767)

        while (running) {
            val length = input.read(packet)
            if (length <= 0) continue

            val buf = ByteBuffer.wrap(packet, 0, length)
            handleIpPacket(buf, output)
        }
    }

    private fun handleIpPacket(buf: ByteBuffer, output: FileOutputStream) {
        if (buf.limit() < 20) return

        val versionIhl = buf.get(0).toInt() and 0xFF
        val ipVersion = versionIhl shr 4
        if (ipVersion != 4) return // только IPv4

        val ihl = (versionIhl and 0x0F) * 4
        val protocol = buf.get(9).toInt() and 0xFF
        if (protocol != 17) return // только UDP

        if (buf.limit() < ihl + 8) return

        val srcPort = buf.getShort(ihl).toInt() and 0xFFFF
        val dstPort = buf.getShort(ihl + 2).toInt() and 0xFFFF

        // DNS: destination port 53 или source port 53 (ответы от нашего форвардинга)
        if (dstPort != 53) return

        val udpPayloadOffset = ihl + 8
        val udpPayloadLength = buf.limit() - udpPayloadOffset
        if (udpPayloadLength <= 0) return

        val dnsData = ByteArray(udpPayloadLength)
        buf.position(udpPayloadOffset)
        buf.get(dnsData)

        handleDnsQuery(
            dnsData = dnsData,
            srcIp = buf.getInt(12),
            dstIp = buf.getInt(16),
            srcPort = srcPort,
            output = output
        )
    }

    private fun handleDnsQuery(
        dnsData: ByteArray,
        srcIp: Int,
        dstIp: Int,
        srcPort: Int,
        output: FileOutputStream
    ) {
        val dns = try { DnsPacket(dnsData) } catch (e: Exception) { return }
        if (!dns.isQuery) return

        val domain = dns.queriedDomain ?: return

        val responseData = if (repository.isBlocked(domain)) {
            dns.questions.firstOrNull()?.let {
                DnsPacket.buildNxDomain(dns.id, it)
            } ?: return
        } else {
            // форвардим к 8.8.8.8 через защищённый сокет
            val socket = DatagramSocket()
            protect(socket)
            socket.close()
            forwardDnsQuery(dnsData) ?: return
        }

        // отправляем DNS-ответ обратно как IP/UDP пакет
        val response = buildUdpPacket(
            srcIp = dstIp,   // меняем местами src/dst
            dstIp = srcIp,
            srcPort = 53,
            dstPort = srcPort,
            payload = responseData
        )
        output.write(response)
    }

    private fun buildUdpPacket(
        srcIp: Int, dstIp: Int,
        srcPort: Int, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val buf = ByteBuffer.allocate(totalLength)

        // IP header
        buf.put(0x45.toByte())          // version=4, IHL=5
        buf.put(0)                       // DSCP/ECN
        buf.putShort(totalLength.toShort())
        buf.putShort(0)                  // ID
        buf.putShort(0x4000.toShort())   // flags: don't fragment
        buf.put(64)                      // TTL
        buf.put(17)                      // protocol: UDP
        buf.putShort(0)                  // checksum (заполним ниже)
        buf.putInt(srcIp)
        buf.putInt(dstIp)

        // IP checksum
        val ipChecksum = checksum(buf.array(), 0, 20)
        buf.putShort(10, ipChecksum.toShort())

        // UDP header
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLength.toShort())
        buf.putShort(0)                  // UDP checksum (опционально для IPv4)

        buf.put(payload)
        return buf.array()
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((offset + length) % 2 != 0) {
            sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
