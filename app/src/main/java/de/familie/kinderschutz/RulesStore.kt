package de.familie.kinderschutz

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.Calendar

/**
 * Speichert alle Regeln der Eltern auf dem Handy (SharedPreferences).
 * Später kann das durch eine Synchronisierung mit der Eltern-App ersetzt werden.
 */
object RulesStore {
    private const val PREFS = "kinderschutz_regeln"
    private const val KEY_PIN = "pin_hash"
    private const val KEY_DAILY = "tageslimit_min"
    private const val KEY_APP_LIMITS = "app_limits"
    private const val KEY_BLOCKED_APPS = "gesperrte_apps"
    private const val KEY_DOMAINS = "gesperrte_domains"
    private const val KEY_BED_START = "ruhezeit_start"
    private const val KEY_BED_END = "ruhezeit_ende"
    private const val KEY_WEBFILTER = "webfilter_an"

    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Eltern-PIN ----------
    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("kinderschutz-" + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun hasPin(c: Context) = p(c).contains(KEY_PIN)
    fun setPin(c: Context, pin: String) = p(c).edit().putString(KEY_PIN, hash(pin)).apply()
    fun checkPin(c: Context, pin: String) = p(c).getString(KEY_PIN, null) == hash(pin)

    // ---------- Bildschirmzeit gesamt ----------
    /** Minuten pro Tag, 0 = kein Limit */
    fun dailyLimitMinutes(c: Context) = p(c).getInt(KEY_DAILY, 0)
    fun setDailyLimitMinutes(c: Context, minutes: Int) =
        p(c).edit().putInt(KEY_DAILY, minutes.coerceAtLeast(0)).apply()

    // ---------- Zeitlimit pro App ----------
    /** Paketname -> Minuten pro Tag */
    fun appLimits(c: Context): Map<String, Int> =
        (p(c).getString(KEY_APP_LIMITS, "") ?: "")
            .split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) parts[1].toIntOrNull()?.let { parts[0] to it } else null
            }
            .toMap()

    fun setAppLimits(c: Context, limits: Map<String, Int>) =
        p(c).edit().putString(
            KEY_APP_LIMITS,
            limits.filter { it.value > 0 }.entries.joinToString(";") { "${it.key}=${it.value}" }
        ).apply()

    // ---------- Komplett gesperrte Apps ----------
    fun blockedApps(c: Context): Set<String> =
        p(c).getStringSet(KEY_BLOCKED_APPS, emptySet())!!.toSet()

    fun setBlockedApps(c: Context, apps: Set<String>) =
        p(c).edit().putStringSet(KEY_BLOCKED_APPS, apps.toSet()).apply()

    // ---------- Gesperrte Webseiten ----------
    fun blockedDomains(c: Context): Set<String> =
        p(c).getStringSet(KEY_DOMAINS, emptySet())!!.toSet()

    /** Macht aus "https://www.tiktok.com/foo" einfach "tiktok.com". */
    fun normalizeDomain(raw: String): String =
        raw.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
            .trimEnd('.')

    fun setBlockedDomains(c: Context, domains: Collection<String>) =
        p(c).edit().putStringSet(
            KEY_DOMAINS,
            domains.map { normalizeDomain(it) }.filter { it.contains('.') }.toSet()
        ).apply()

    // ---------- Ruhezeit (z. B. 21:00 bis 07:00) ----------
    /** Minuten seit Mitternacht, -1 = aus */
    fun bedtimeStart(c: Context) = p(c).getInt(KEY_BED_START, -1)
    fun bedtimeEnd(c: Context) = p(c).getInt(KEY_BED_END, -1)
    fun setBedtime(c: Context, start: Int, end: Int) =
        p(c).edit().putInt(KEY_BED_START, start).putInt(KEY_BED_END, end).apply()

    fun isBedtimeNow(c: Context): Boolean {
        val s = bedtimeStart(c)
        val e = bedtimeEnd(c)
        if (s < 0 || e < 0 || s == e) return false
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (s < e) now in s until e else (now >= s || now < e) // über Mitternacht
    }

    // ---------- Webfilter an/aus ----------
    fun webFilterEnabled(c: Context) = p(c).getBoolean(KEY_WEBFILTER, false)
    fun setWebFilterEnabled(c: Context, on: Boolean) =
        p(c).edit().putBoolean(KEY_WEBFILTER, on).apply()
}
