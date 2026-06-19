package com.blocksite.dns

import java.nio.ByteBuffer

data class DnsQuestion(val name: String, val type: Int, val clazz: Int)

class DnsPacket(val raw: ByteArray) {
    val id: Int
    val isQuery: Boolean
    val questions: List<DnsQuestion>

    init {
        val buf = ByteBuffer.wrap(raw)
        id = buf.short.toInt() and 0xFFFF
        val flags = buf.short.toInt() and 0xFFFF
        isQuery = (flags and 0x8000) == 0
        val questionCount = buf.short.toInt() and 0xFFFF
        buf.position(buf.position() + 6) // skip AN, NS, AR counts

        questions = (0 until questionCount).map {
            val name = readDnsName(buf)
            val type = buf.short.toInt() and 0xFFFF
            val clazz = buf.short.toInt() and 0xFFFF
            DnsQuestion(name, type, clazz)
        }
    }

    private fun readDnsName(buf: ByteBuffer): String {
        val parts = mutableListOf<String>()
        while (true) {
            val len = buf.get().toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) {
                // pointer — skip (simplified, не используется в query)
                buf.get()
                break
            }
            val bytes = ByteArray(len)
            buf.get(bytes)
            parts.add(String(bytes))
        }
        return parts.joinToString(".")
    }

    val queriedDomain: String?
        get() = questions.firstOrNull()?.name?.lowercase()?.trimEnd('.')

    companion object {
        fun buildNxDomain(queryId: Int, question: DnsQuestion): ByteArray {
            val buf = ByteBuffer.allocate(512)
            buf.putShort(queryId.toShort())
            // flags: QR=1, OPCODE=0, AA=1, RCODE=3 (NXDOMAIN)
            buf.putShort(0x8403.toShort())
            buf.putShort(1) // QDCOUNT
            buf.putShort(0) // ANCOUNT
            buf.putShort(0) // NSCOUNT
            buf.putShort(0) // ARCOUNT

            encodeDnsName(buf, question.name)
            buf.putShort(question.type.toShort())
            buf.putShort(question.clazz.toShort())

            return buf.array().copyOf(buf.position())
        }

        private fun encodeDnsName(buf: ByteBuffer, name: String) {
            name.split(".").forEach { label ->
                if (label.isNotEmpty()) {
                    buf.put(label.length.toByte())
                    buf.put(label.toByteArray())
                }
            }
            buf.put(0)
        }
    }
}
