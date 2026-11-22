package de.familie.kinderschutz

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Abgleich mit der Eltern-Verwaltung über die Firebase-REST-Schnittstelle
 * (ohne Zusatz-Bibliothek). Das Handy meldet sich anonym an, wird einmal mit
 * einem 6-stelligen Code einer Familie zugeordnet und holt dann regelmäßig
 * die Regeln ab und meldet die Nutzungszeit.
 */
object CloudSync {
    private const val PREFS = "kinderschutz_cloud"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val docs get() = "https://firestore.googleapis.com/v1/projects/${CloudConfig.PROJECT_ID}/databases/(default)/documents"

    fun isConfigured() = CloudConfig.API_KEY.isNotBlank() && CloudConfig.PROJECT_ID.isNotBlank()
    fun isPaired(c: Context) = p(c).getString("deviceId", null) != null
    fun deviceName(c: Context): String? = p(c).getString("deviceName", null)
    fun lastSync(c: Context) = p(c).getLong("lastSync", 0L)
    fun lastError(c: Context): String? = p(c).getString("lastError", null)

    // ---------------- HTTP ----------------
    private class HttpError(val code: Int, msg: String) : IOException(msg)

    private fun http(method: String, url: String, body: String?, token: String?, form: Boolean = false): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection kennt kein PATCH; Google versteht diesen Umweg
            conn.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpError(code, "HTTP $code: ${text.take(300)}")
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun str(s: String?) = if (s == null) JSONObject().put("nullValue", JSONObject.NULL) else JSONObject().put("stringValue", s)
    private fun fields(vararg f: Pair<String, JSONObject>) =
        JSONObject().put("fields", JSONObject().apply { f.forEach { put(it.first, it.second) } }).toString()
    private fun JSONObject.field(name: String): String? =
        optJSONObject("fields")?.optJSONObject(name)?.optString("stringValue", null)
    private fun mask(vararg names: String) = names.joinToString("&") { "updateMask.fieldPaths=$it" }

    // ---------------- Anmeldung (anonym) ----------------
    @Synchronized
    private fun token(c: Context): String {
        val prefs = p(c)
        val cached = prefs.getString("idToken", null)
        if (cached != null && System.currentTimeMillis() < prefs.getLong("tokenExp", 0)) return cached

        val refresh = prefs.getString("refreshToken", null)
        val idToken: String
        val newRefresh: String
        val expiresIn: Long
        if (refresh == null) {
            val r = http(
                "POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${CloudConfig.API_KEY}",
                JSONObject().put("returnSecureToken", true).toString(), null
            )
            idToken = r.getString("idToken")
            newRefresh = r.getString("refreshToken")
            expiresIn = r.getString("expiresIn").toLong()
            prefs.edit().putString("uid", r.getString("localId")).apply()
        } else {
            val r = http(
                "POST", "https://securetoken.googleapis.com/v1/token?key=${CloudConfig.API_KEY}",
                "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"), null, form = true
            )
            idToken = r.getString("id_token")
            newRefresh = r.getString("refresh_token")
            expiresIn = r.getString("expires_in").toLong()
            prefs.edit().putString("uid", r.getString("user_id")).apply()
        }
        prefs.edit()
            .putString("idToken", idToken)
            .putString("refreshToken", newRefresh)
            .putLong("tokenExp", System.currentTimeMillis() + (expiresIn - 120) * 1000)
            .apply()
        return idToken
    }

    // ---------------- Verbinden mit Code ----------------
    /** done(null) = verbunden, sonst Fehlermeldung für die Eltern. */
    fun pair(c: Context, code: String, done: (String?) -> Unit) {
        val app = c.applicationContext
        io.execute {
            val error = try {
                val t = token(app)
                val uid = p(app).getString("uid", null) ?: throw IOException("Anmeldung fehlgeschlagen")
                val pc = http("GET", "$docs/pairCodes/$code", null, t)
                val familyId = pc.field("familyId") ?: throw IOException("Code ungültig")
                val deviceId = pc.field("deviceId") ?: throw IOException("Code ungültig")
                http("PATCH", "$docs/pairCodes/$code?${mask("claimedBy")}", fields("claimedBy" to str(uid)), t)
                http("PATCH", "$docs/families/$familyId/devices/$deviceId?${mask("deviceUid")}", fields("deviceUid" to str(uid)), t)
                p(app).edit().putString("familyId", familyId).putString("deviceId", deviceId).apply()
                syncBlocking(app)
                null
            } catch (e: HttpError) {
                when (e.code) {
                    404 -> "Diesen Code gibt es nicht."
                    403 -> "Code abgelaufen oder schon benutzt. Bitte in der Verwaltung einen neuen erstellen."
                    else -> "Verbinden fehlgeschlagen (${e.code})."
                }
            } catch (e: Exception) {
                "Keine Verbindung zum Internet?"
            }
            main.post { done(error) }
        }
    }

    fun unpair(c: Context) {
        p(c).edit().remove("familyId").remove("deviceId").remove("deviceName").apply()
    }

    // ---------------- Regelmäßiger Abgleich ----------------
    fun syncNow(c: Context, done: ((String?) -> Unit)? = null) {
        val app = c.applicationContext
        if (!isConfigured() || !isPaired(app)) return
        io.execute {
            val error = try { syncBlocking(app); null } catch (e: Exception) { e.message ?: "Fehler" }
            p(app).edit().putString("lastError", error).apply()
            done?.let { cb -> main.post { cb(error) } }
        }
    }

    private fun syncBlocking(c: Context) {
        val prefs = p(c)
        val familyId = prefs.getString("familyId", null) ?: return
        val deviceId = prefs.getString("deviceId", null) ?: return
        val path = "$docs/families/$familyId/devices/$deviceId"
        val t = token(c)

        // 1. Regeln holen
        val doc = try {
            http("GET", path, null, t)
        } catch (e: HttpError) {
            if (e.code == 404 || e.code == 403) unpair(c) // in der Verwaltung entfernt
            throw e
        }
        doc.field("name")?.let { prefs.edit().putString("deviceName", it).apply() }
        doc.field("rulesJson")?.takeIf { it.isNotBlank() }?.let { applyRules(c, JSONObject(it)) }

        // 2. Nutzung und App-Liste melden
        val usage = if (UsageHelper.hasPermission(c)) UsageHelper.usageToday(c) else emptyMap()
        val usageApps = JSONObject()
        usage.filter { it.key != c.packageName && it.value >= 60_000 }.forEach { usageApps.put(it.key, it.value / 60_000) }
        val usageJson = JSONObject()
            .put("date", SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()))
            .put("totalMin", UsageHelper.totalScreenTimeMs(c, usage) / 60_000)
            .put("apps", usageApps)
        val appsJson = JSONObject()
        UsageHelper.launchableApps(c).forEach { appsJson.put(it.first, it.second) }

        http(
            "PATCH", "$path?${mask("usageJson", "appsJson", "lastSeen")}",
            fields(
                "usageJson" to str(usageJson.toString()),
                "appsJson" to str(appsJson.toString()),
                "lastSeen" to JSONObject().put("timestampValue", Instant.now().toString()),
            ), t
        )
        prefs.edit().putLong("lastSync", System.currentTimeMillis()).putString("lastError", null).apply()
    }

    /** Übernimmt die Regeln aus der Verwaltung in den lokalen Speicher. */
    private fun applyRules(c: Context, r: JSONObject) {
        RulesStore.setDailyLimitMinutes(c, r.optInt("dailyLimitMinutes", 0))
        RulesStore.setBedtime(c, r.optInt("bedtimeStart", -1), r.optInt("bedtimeEnd", -1))
        RulesStore.setBlockedApps(c, r.optJSONArray("blockedApps").toStringSet())
        RulesStore.setBlockedDomains(c, r.optJSONArray("blockedDomains").toStringSet())
        val limits = HashMap<String, Int>()
        r.optJSONObject("appLimits")?.let { o -> o.keys().forEach { k -> limits[k] = o.optInt(k, 0) } }
        RulesStore.setAppLimits(c, limits)
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it, null) }.toSet()
    }
}
