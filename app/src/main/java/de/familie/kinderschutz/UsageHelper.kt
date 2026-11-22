package de.familie.kinderschutz

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import java.util.Calendar

/** Misst mit der Android-Funktion "Nutzungszugriff", wie lange jede App heute offen war. */
object UsageHelper {

    fun hasPermission(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Nutzungszeit heute pro App (Paketname -> Millisekunden). */
    fun usageToday(c: Context): Map<String, Long> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(startOfToday(), now)
        val result = HashMap<String, Long>()
        val openSince = HashMap<String, Long>()
        val openCount = HashMap<String, Int>()
        val e = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            @Suppress("DEPRECATION")
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val n = openCount[pkg] ?: 0
                    if (n == 0) openSince[pkg] = e.timeStamp
                    openCount[pkg] = n + 1
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val n = openCount[pkg] ?: 0
                    if (n > 0) {
                        openCount[pkg] = n - 1
                        if (n == 1) {
                            val start = openSince.remove(pkg) ?: e.timeStamp
                            result[pkg] = (result[pkg] ?: 0L) + (e.timeStamp - start)
                        }
                    }
                }
            }
        }
        // Apps, die gerade noch offen sind
        for ((pkg, start) in openSince) result[pkg] = (result[pkg] ?: 0L) + (now - start)
        return result
    }

    fun launcherPackage(c: Context): String? =
        c.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

    /** Gesamte Bildschirmzeit heute, ohne Startbildschirm und ohne diese App selbst. */
    fun totalScreenTimeMs(c: Context, usage: Map<String, Long>): Long {
        val ignore = setOfNotNull(c.packageName, launcherPackage(c), "com.android.systemui")
        return usage.filterKeys { it !in ignore }.values.sum()
    }

    /** Alle Apps mit Symbol auf dem Startbildschirm: Paketname -> Name. */
    fun launchableApps(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != c.packageName }
            .sortedBy { it.second.lowercase() }
    }

    fun appLabel(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    fun formatMinutes(ms: Long): String {
        val min = ms / 60_000
        return if (min >= 60) "${min / 60} Std ${min % 60} Min" else "$min Min"
    }
}
