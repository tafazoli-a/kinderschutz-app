package de.familie.kinderschutz

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent

/**
 * Bedienungshilfe-Dienst: Android meldet uns jedes Mal, wenn eine andere App in den
 * Vordergrund kommt. Ist die App gesperrt oder ihre Zeit um, schicken wir das Kind
 * zum Startbildschirm und zeigen den Sperrbildschirm.
 */
class AppGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var lastBlockAt = 0L

    /** Apps, die nie gesperrt werden (Startbildschirm, Telefon, Tastatur, diese App). */
    private fun alwaysAllowed(): Set<String> {
        val dialer = (getSystemService(TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
        val keyboard = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore("/")
        return setOfNotNull(
            packageName, "com.android.systemui", UsageHelper.launcherPackage(this),
            dialer, keyboard, "com.android.server.telecom", "com.android.emergency"
        )
    }

    // Alle 30 Sekunden prüfen, damit ein Limit auch greift, wenn das Kind in der App bleibt.
    private var ticks = 0

    private val periodic = object : Runnable {
        override fun run() {
            if (ticks++ % 10 == 0) CloudSync.syncNow(this@AppGuardService) // alle 5 Minuten mit der Verwaltung abgleichen
            currentPkg?.let { check(it) }
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onServiceConnected() {
        handler.post(periodic)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        currentPkg = pkg
        check(pkg)
    }

    private fun check(pkg: String) {
        if (pkg in alwaysAllowed()) return
        val reason = RuleChecker.blockReason(this, pkg) ?: return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 1500) return
        lastBlockAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.EXTRA_REASON, reason)
                .putExtra(BlockActivity.EXTRA_APP, UsageHelper.appLabel(this, pkg))
        )
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(periodic)
        super.onDestroy()
    }
}
