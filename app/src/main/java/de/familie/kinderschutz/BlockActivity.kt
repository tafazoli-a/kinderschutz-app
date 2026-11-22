package de.familie.kinderschutz

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Der Bildschirm, den das Kind sieht, wenn eine App gesperrt ist. */
class BlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Gesperrt"
        val app = intent.getStringExtra(EXTRA_APP)
        val pad = (32 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#1E3A5F"))
        }
        layout.addView(TextView(this).apply {
            text = "⏸"
            textSize = 64f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        if (app != null) layout.addView(TextView(this).apply {
            text = app
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        layout.addView(TextView(this).apply {
            text = reason
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#DDE6F0"))
            setPadding(0, pad / 2, 0, pad)
        })
        layout.addView(Button(this).apply {
            text = "Zum Startbildschirm"
            setOnClickListener { goHome() }
        })
        setContentView(layout)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() = goHome()

    companion object {
        const val EXTRA_REASON = "grund"
        const val EXTRA_APP = "app"
    }
}
