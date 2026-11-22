package de.familie.kinderschutz

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Lokaler Webfilter. Es wird KEIN Datenverkehr ins Internet umgeleitet:
 * Android schickt nur die DNS-Fragen ("Wo ist tiktok.com?") an diesen Dienst.
 * Gesperrte Adressen bekommen "gibt es nicht", alle anderen gehen an einen
 * familienfreundlichen DNS-Server weiter, der zusätzlich Erwachseneninhalte filtert.
 */
class WebFilterVpnService : VpnService() {

    @Volatile private var running = false
    private var tun: ParcelFileDescriptor? = null
    private val pool = Executors.newFixedThreadPool(4)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFilter()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) startFilter()
        return START_STICKY
    }

    private fun startFilter() {
        val fd = Builder()
            .setSession("KinderSchutz Webfilter")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)   // nur der "falsche" DNS-Server läuft über uns
            .setBlocking(true)
            .establish() ?: return    // null = Eltern haben den Webfilter noch nicht erlaubt
        tun = fd
        running = true
        Thread({ loop(fd) }, "webfilter").start()
    }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val len = try { input.read(buf) } catch (e: IOException) { break }
            if (len <= 0) continue
            val packet = buf.copyOf(len)
            pool.execute { handle(packet, output) }
        }
    }

    private fun handle(packet: ByteArray, out: FileOutputStream) {
        try {
            val q = DnsPacket.parseQuery(packet) ?: return
            val answer = if (isBlocked(q.domain)) DnsPacket.blockedAnswer(q.dns) else forward(q.dns) ?: return
            val reply = DnsPacket.wrapReply(q, answer)
            synchronized(out) { out.write(reply) }
        } catch (_: Exception) {
            // eine kaputte Anfrage soll den Filter nicht stoppen
        }
    }

    private fun isBlocked(domain: String): Boolean {
        val d = domain.trimEnd('.')
        // "tiktok.com" sperrt auch "www.tiktok.com" und "m.tiktok.com"
        return RulesStore.blockedDomains(this).any { d == it || d.endsWith(".$it") }
    }

    private fun forward(dns: ByteArray): ByteArray? {
        DatagramSocket().use { s ->
            protect(s) // diese Verbindung nicht wieder in den Filter schicken
            s.soTimeout = 5000
            s.send(DatagramPacket(dns, dns.size, InetAddress.getByName(UPSTREAM_DNS), 53))
            val buf = ByteArray(4096)
            val resp = DatagramPacket(buf, buf.size)
            s.receive(resp)
            return buf.copyOf(resp.length)
        }
    }

    private fun stopFilter() {
        running = false
        try { tun?.close() } catch (_: IOException) {}
        tun = null
    }

    override fun onRevoke() {
        stopFilter()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopFilter()
        pool.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "de.familie.kinderschutz.STOP_WEBFILTER"
        private const val VPN_ADDRESS = "10.111.0.1"
        private const val FAKE_DNS = "10.111.0.2"
        // Cloudflare "for Families": blockiert zusätzlich Schadsoftware und Erwachseneninhalte.
        // ANNAHME: kann durch einen anderen Familienfilter ersetzt werden.
        private const val UPSTREAM_DNS = "1.1.1.3"
    }
}
