package de.familie.kinderschutz

import android.content.Context

/** Entscheidet, ob eine App gerade gesperrt werden muss. Gibt den Grund zurück oder null. */
object RuleChecker {
    fun blockReason(c: Context, pkg: String): String? {
        if (pkg in RulesStore.blockedApps(c)) return "Diese App ist gesperrt."
        if (RulesStore.isBedtimeNow(c)) return "Jetzt ist Ruhezeit. Das Handy macht Pause."

        if (!UsageHelper.hasPermission(c)) return null // ohne Messung keine Zeitlimits
        val usage = UsageHelper.usageToday(c)

        val appLimit = RulesStore.appLimits(c)[pkg]
        if (appLimit != null && (usage[pkg] ?: 0L) >= appLimit * 60_000L) {
            return "Die Zeit für diese App ist für heute aufgebraucht."
        }
        val daily = RulesStore.dailyLimitMinutes(c)
        if (daily > 0 && UsageHelper.totalScreenTimeMs(c, usage) >= daily * 60_000L) {
            return "Die Bildschirmzeit für heute ist aufgebraucht."
        }
        return null
    }
}
