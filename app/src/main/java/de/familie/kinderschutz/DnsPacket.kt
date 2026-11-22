package de.familie.kinderschutz

/**
 * Liest DNS-Anfragen aus IPv4/UDP-Paketen und baut Antwort-Pakete.
 * DNS ist das "Telefonbuch" des Internets: Bevor ein Browser tiktok.com öffnet,
 * fragt er nach der Adresse. Genau diese Frage fangen wir ab.
 */
object DnsPacket {

    class Query(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dns: ByteArray,
        val domain: String,
    )

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v shr 8).toByte()
        b[i + 1] = v.toByte()
    }

    fun parseQuery(p: ByteArray): Query? {
        if (p.size < 20 + 8 + 12) return null
        if ((p[0].toInt() shr 4) and 0xF != 4) return null          // nur IPv4
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p[9].toInt() != 17) return null                           // nur UDP
        if (p.size < ihl + 8 + 12) return null
        val dstPort = u16(p, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(p, ihl + 4)
        val end = minOf(p.size, ihl + udpLen)
        if (end <= ihl + 8 + 12) return null
        val dns = p.copyOfRange(ihl + 8, end)
        val domain = readName(dns, 12) ?: return null
        return Query(
            srcIp = p.copyOfRange(12, 16),
            dstIp = p.copyOfRange(16, 20),
            srcPort = u16(p, ihl),
            dstPort = dstPort,
            dns = dns,
            domain = domain.lowercase(),
        )
    }

    private fun readName(d: ByteArray, start: Int): String? {
        val sb = StringBuilder()
        var i = start
        while (i < d.size) {
            val len = d[i].toInt() and 0xFF
            if (len == 0) return sb.toString()
            if (len and 0xC0 != 0) return null      // Komprimierung kommt in Anfragen nicht vor
            if (i + 1 + len > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(d, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        return null
    }

    private fun questionEnd(d: ByteArray): Int {
        var i = 12
        while (i < d.size && d[i].toInt() != 0) i += 1 + (d[i].toInt() and 0xFF)
        return minOf(i + 1 + 4, d.size) // Null-Byte + Typ + Klasse
    }

    /** Antwort "Diese Adresse gibt es nicht" (NXDOMAIN). */
    fun blockedAnswer(q: ByteArray): ByteArray {
        val r = q.copyOf(questionEnd(q))
        r[2] = (0x80 or (q[2].toInt() and 0x79)).toByte() // Antwort-Bit, Opcode und RD übernehmen
        r[3] = (0x80 or 3).toByte()                        // RA=1, Fehlercode 3 = NXDOMAIN
        put16(r, 4, 1)  // 1 Frage
        put16(r, 6, 0)  // 0 Antworten
        put16(r, 8, 0)
        put16(r, 10, 0)
        return r
    }

    /** Packt eine DNS-Antwort in ein IPv4/UDP-Paket zurück an die anfragende App. */
    fun wrapReply(q: Query, dns: ByteArray): ByteArray {
        val total = 20 + 8 + dns.size
        val r = ByteArray(total)
        r[0] = 0x45                      // IPv4, 20 Byte Kopf
        put16(r, 2, total)
        r[6] = 0x40                      // "nicht fragmentieren"
        r[8] = 64                        // TTL
        r[9] = 17                        // UDP
        System.arraycopy(q.dstIp, 0, r, 12, 4)
        System.arraycopy(q.srcIp, 0, r, 16, 4)
        put16(r, 10, checksum(r, 0, 20))
        put16(r, 20, q.dstPort)
        put16(r, 22, q.srcPort)
        put16(r, 24, 8 + dns.size)
        // UDP-Prüfsumme 0 = "keine", bei IPv4 erlaubt
        System.arraycopy(dns, 0, r, 28, dns.size)
        return r
    }

    private fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len) {
            sum += u16(b, i)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
