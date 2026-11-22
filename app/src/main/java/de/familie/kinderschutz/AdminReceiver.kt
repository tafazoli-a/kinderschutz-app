package de.familie.kinderschutz

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Solange die App Geräte-Administrator ist, kann sie nicht einfach deinstalliert werden. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Damit wird der Kinderschutz ausgeschaltet. Bitte nur mit Erlaubnis der Eltern."
}
