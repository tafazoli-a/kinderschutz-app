package de.familie.kinderschutz

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Eltern-Bereich. Erst PIN, dann alle Einstellungen auf einer Seite.
 * Die Oberfläche wird bewusst im Code gebaut (keine Layout-Dateien), damit alles an einer Stelle steht.
 */
class MainActivity : Activity() {

    private var unlocked = false
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showPinScreen(forceNew = false)
    }

    override fun onResume() {
        super.onResume()
        // Nach 2 Minuten im Hintergrund wieder sperren
        if (unlocked && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 120_000) unlocked = false
        if (unlocked) showParentScreen() else showPinScreen(forceNew = false)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    // ---------------- kleine Bausteine ----------------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun screen(build: LinearLayout.() -> Unit) {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        l.build()
        setContentView(ScrollView(this).apply { addView(l) })
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 22f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(8))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 18f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(24), 0, dp(4))
    }

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 15f }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; setOnClickListener { onClick() }
    }

    private fun field(value: String, hint: String, type: Int) = EditText(this).apply {
        setText(value); this.hint = hint; inputType = type
    }

    private fun statusRow(name: String, ok: Boolean, action: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                text = (if (ok) "✅ " else "❌ ") + name
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(button(action, onClick))
        }

    // ---------------- PIN ----------------
    private fun showPinScreen(forceNew: Boolean) {
        val first = forceNew || !RulesStore.hasPin(this)
        screen {
            addView(title(if (first) "Eltern-PIN festlegen" else "Eltern-Bereich"))
            addView(label(
                if (first) "Wähle eine PIN mit mindestens 4 Ziffern. Dein Kind darf sie nicht kennen."
                else "Bitte Eltern-PIN eingeben."
            ))
            val pin = field("", "PIN", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            addView(pin)
            addView(button(if (first) "PIN speichern" else "Entsperren") {
                val v = pin.text.toString()
                when {
                    first && v.length < 4 -> toast("Mindestens 4 Ziffern")
                    first -> { RulesStore.setPin(this@MainActivity, v); toast("PIN gespeichert"); unlock() }
                    RulesStore.checkPin(this@MainActivity, v) -> unlock()
                    else -> { pin.text.clear(); toast("Falsche PIN") }
                }
            })
        }
    }

    private fun unlock() {
        unlocked = true
        stoppedAt = 0
        showParentScreen()
    }

    // ---------------- Eltern-Bereich ----------------
    private fun showParentScreen() {
        val ctx = this
        screen {
            addView(title("KinderSchutz – Eltern"))

            // 0. Eltern-Verwaltung
            addView(section("Eltern-Verwaltung"))
            when {
                !CloudSync.isConfigured() ->
                    addView(label("Noch nicht eingerichtet. Regeln werden nur hier am Handy eingestellt."))
                CloudSync.isPaired(ctx) -> {
                    val last = CloudSync.lastSync(ctx)
                    val time = if (last == 0L) "noch nie" else
                        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(last))
                    addView(label("✅ Verbunden als „${CloudSync.deviceName(ctx) ?: "Kinderhandy"}“. Letzter Abgleich: $time"))
                    CloudSync.lastError(ctx)?.let { addView(label("⚠️ $it")) }
                    addView(label("Die Regeln kommen aus der Verwaltung. Änderungen hier werden beim nächsten Abgleich überschrieben."))
                    addView(button("Jetzt abgleichen") {
                        CloudSync.syncNow(ctx) { err -> toast(err ?: "Abgeglichen"); showParentScreen() }
                    })
                }
                else -> {
                    addView(label("Gib den 6-stelligen Code aus der Eltern-Verwaltung ein."))
                    val code = field("", "Code", InputType.TYPE_CLASS_NUMBER)
                    addView(code)
                    addView(button("Verbinden") {
                        val v = code.text.toString().trim()
                        if (v.length != 6) { toast("Der Code hat 6 Ziffern"); return@button }
                        toast("Verbinde …")
                        CloudSync.pair(ctx, v) { err -> toast(err ?: "Verbunden!"); showParentScreen() }
                    })
                }
            }

            // 1. Einrichtung
            addView(section("1. Einrichtung (einmalig)"))
            addView(statusRow("Bildschirmzeit messen", UsageHelper.hasPermission(ctx), "Erlauben") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
            addView(statusRow("Apps sperren (Bedienungshilfe)", isAccessibilityOn(), "Erlauben") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(statusRow("Schutz vor Löschen", isAdminOn(), "Erlauben") { requestAdmin() })
            val webOn = RulesStore.webFilterEnabled(ctx)
            addView(statusRow("Webfilter", webOn, if (webOn) "Aus" else "Ein") { toggleWebFilter() })

            // 2. Heute
            addView(section("2. Heute genutzt"))
            if (UsageHelper.hasPermission(ctx)) {
                val usage = UsageHelper.usageToday(ctx)
                addView(label("Gesamt: " + UsageHelper.formatMinutes(UsageHelper.totalScreenTimeMs(ctx, usage))))
                usage.entries
                    .filter { it.key != packageName && it.value >= 60_000 }
                    .sortedByDescending { it.value }
                    .take(8)
                    .forEach { addView(label("• ${UsageHelper.appLabel(ctx, it.key)}: ${UsageHelper.formatMinutes(it.value)}")) }
            } else {
                addView(label("Erst oben „Bildschirmzeit messen“ erlauben."))
            }

            // 3. Bildschirmzeit + Ruhezeit
            addView(section("3. Bildschirmzeit pro Tag"))
            val daily = field(
                RulesStore.dailyLimitMinutes(ctx).takeIf { it > 0 }?.toString() ?: "",
                "Minuten pro Tag (leer = kein Limit)", InputType.TYPE_CLASS_NUMBER
            )
            addView(daily)
            addView(label("Ruhezeit (z. B. 21:00 bis 07:00, leer = aus)"))
            val bedFrom = field(formatTime(RulesStore.bedtimeStart(ctx)), "von 21:00", InputType.TYPE_CLASS_DATETIME)
            val bedTo = field(formatTime(RulesStore.bedtimeEnd(ctx)), "bis 07:00", InputType.TYPE_CLASS_DATETIME)
            addView(bedFrom)
            addView(bedTo)
            addView(button("Zeiten speichern") {
                RulesStore.setDailyLimitMinutes(ctx, daily.text.toString().toIntOrNull() ?: 0)
                val s = parseTime(bedFrom.text.toString())
                val e = parseTime(bedTo.text.toString())
                if ((s == null) != (e == null) || s == -2 || e == -2) {
                    toast("Ruhezeit bitte als HH:MM eingeben, beide Felder")
                } else {
                    RulesStore.setBedtime(ctx, s ?: -1, e ?: -1)
                    toast("Gespeichert")
                }
            })

            // 4. Apps
            addView(section("4. Apps: Zeitlimit oder sperren"))
            addView(label("Minuten pro Tag eintragen (leer = kein Limit) oder „Sperren“ ankreuzen."))
            val limits = RulesStore.appLimits(ctx)
            val blocked = RulesStore.blockedApps(ctx)
            val rows = UsageHelper.launchableApps(ctx).map { (pkg, name) ->
                val minutes = field(limits[pkg]?.toString() ?: "", "Min", InputType.TYPE_CLASS_NUMBER).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val block = CheckBox(context).apply { text = "Sperren"; isChecked = pkg in blocked }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextView(context).apply {
                        text = name
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(minutes)
                    addView(block)
                })
                Triple(pkg, minutes, block)
            }
            addView(button("App-Regeln speichern") {
                RulesStore.setAppLimits(ctx, rows.mapNotNull { (pkg, m, _) ->
                    m.text.toString().toIntOrNull()?.let { pkg to it }
                }.toMap())
                RulesStore.setBlockedApps(ctx, rows.filter { it.third.isChecked }.map { it.first }.toSet())
                toast("Gespeichert")
            })

            // 5. Webseiten
            addView(section("5. Gesperrte Webseiten"))
            addView(label("Eine Adresse pro Zeile, z. B. tiktok.com. Unterseiten wie m.tiktok.com sind automatisch mit gesperrt. Funktioniert nur, wenn oben der Webfilter an ist."))
            val domains = field(
                RulesStore.blockedDomains(ctx).sorted().joinToString("\n"),
                "tiktok.com", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            ).apply { minLines = 4 }
            addView(domains)
            addView(button("Webseiten speichern") {
                RulesStore.setBlockedDomains(ctx, domains.text.lines())
                toast("Gespeichert: ${RulesStore.blockedDomains(ctx).size} Adressen")
            })

            // 6. Sonstiges
            addView(section("6. Sonstiges"))
            addView(button("PIN ändern") { showPinScreen(forceNew = true) })
            addView(button("Eltern-Bereich sperren") { unlocked = false; showPinScreen(forceNew = false) })
        }
    }

    // ---------------- Hilfsfunktionen ----------------
    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    private fun adminComponent() = ComponentName(this, AdminReceiver::class.java)

    private fun isAdminOn() =
        (getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComponent())

    private fun requestAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Damit dein Kind KinderSchutz nicht einfach löschen kann.")
        )
    }

    private fun toggleWebFilter() {
        if (RulesStore.webFilterEnabled(this)) {
            RulesStore.setWebFilterEnabled(this, false)
            startService(Intent(this, WebFilterVpnService::class.java).setAction(WebFilterVpnService.ACTION_STOP))
            showParentScreen()
            return
        }
        val consent = VpnService.prepare(this)
        if (consent != null) startActivityForResult(consent, REQ_VPN) else startWebFilter()
    }

    private fun startWebFilter() {
        RulesStore.setWebFilterEnabled(this, true)
        startService(Intent(this, WebFilterVpnService::class.java))
        showParentScreen()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) startWebFilter()
    }

    private fun formatTime(min: Int) = if (min < 0) "" else "%02d:%02d".format(min / 60, min % 60)

    /** null = leer, -2 = ungültig, sonst Minuten seit Mitternacht */
    private fun parseTime(s: String): Int? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val parts = t.split(":", ".")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return -2
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (h !in 0..23 || m !in 0..59) return -2
        return h * 60 + m
    }

    companion object {
        private const val REQ_VPN = 1
    }
}
