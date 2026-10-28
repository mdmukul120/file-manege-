package com.example.aifiles

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.work.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val BG = Color.parseColor("#0F1220")
    private val CARD = Color.parseColor("#1A1F36")
    private val FIELD = Color.parseColor("#262C4A")
    private val ACCENT = Color.parseColor("#7C5CFF")
    private val ACCENT2 = Color.parseColor("#00B8F0")
    private val TEXT = Color.parseColor("#EAF0FF")
    private val MUTED = Color.parseColor("#8A93B2")
    private val GREEN = Color.parseColor("#3DDC97")
    private val AMBER = Color.parseColor("#FFB84D")

    private lateinit var prefs: SharedPreferences
    private lateinit var logView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var totalView: TextView
    private lateinit var lastView: TextView
    private lateinit var permStatus: TextView
    private lateinit var permButton: Button
    private lateinit var runBtn: Button
    private lateinit var undoBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(16), dp(40), dp(16), dp(24))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACCENT, ACCENT2)).apply { cornerRadius = dp(24).toFloat() }
            setPadding(dp(22), dp(26), dp(22), dp(26))
        }
        header.addView(tv("\u2728 AI File Organizer", 26f, Color.WHITE, true))
        header.addView(tv("Your phone's files, sorted automatically by Gemini", 14f, Color.parseColor("#E6EEFF")).apply { setPadding(0, dp(6), 0, 0) })
        root.addView(header)

        // Stats
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        totalView = tv("0", 24f, TEXT, true)
        lastView = tv("Never", 14f, TEXT, true).apply { setPadding(0, dp(6), 0, 0) }
        val c1 = card().apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(12); marginEnd = dp(6) }
            addView(totalView); addView(tv("Files organized", 12f, MUTED))
        }
        val c2 = card().apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(12); marginStart = dp(6) }
            addView(lastView); addView(tv("Last run", 12f, MUTED).apply { setPadding(0, dp(2), 0, 0) })
        }
        stats.addView(c1); stats.addView(c2)
        root.addView(stats)

        // Permission
        permStatus = tv("", 14f, AMBER, true)
        permButton = button("Grant file access", true).apply { setOnClickListener { requestAccess() } }
        root.addView(card().apply { addView(permStatus); addView(permButton) })

        // Settings
        val keyInput = input("Gemini API key", prefs.getString("key", "") ?: "", true)
        val modelInput = input("Model", prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash", false)
        val save = button("Save settings", false)
        save.setOnClickListener {
            prefs.edit().putString("key", keyInput.text.toString().trim())
                .putString("model", modelInput.text.toString().trim().ifEmpty { "gemini-2.5-flash" }).apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        root.addView(card().apply {
            addView(tv("Gemini settings", 16f, TEXT, true))
            addView(keyInput); addView(modelInput); addView(save)
        })

        // Actions
        runBtn = button("\u26A1  Organize now", true)
        undoBtn = button("\u21A9  Undo last run", false)
        progress = ProgressBar(this).apply { isIndeterminate = true; visibility = View.GONE }
        val auto = Switch(this).apply {
            text = "Auto-organize every day"
            setTextColor(TEXT); textSize = 15f
            isChecked = prefs.getBoolean("auto", false)
            setPadding(0, dp(6), 0, dp(6))
        }
        runBtn.setOnClickListener { save.performClick(); work("Organize") { Organizer.run(this) { s -> log(s) } } }
        undoBtn.setOnClickListener { work("Undo") { Organizer.undo(this) { s -> log(s) } } }
        auto.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("auto", on).apply()
            val wm = WorkManager.getInstance(this)
            if (on) {
                val req = PeriodicWorkRequestBuilder<OrganizeWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
                wm.enqueueUniquePeriodicWork("auto-organize", ExistingPeriodicWorkPolicy.UPDATE, req)
            } else wm.cancelUniqueWork("auto-organize")
        }
        root.addView(card().apply {
            addView(runBtn); addView(undoBtn); addView(progress)
            addView(auto.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) } })
        })

        // Log
        logView = tv("Activity will appear here.", 12f, MUTED).apply { typeface = Typeface.MONOSPACE }
        root.addView(card().apply { addView(tv("Activity", 16f, TEXT, true)); addView(logView.apply { setPadding(0, dp(8), 0, 0) }) })

        setContentView(ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(root) })
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(r, p, g); refresh() }

    private fun refresh() {
        val ok = Organizer.hasAccess(this)
        permStatus.text = if (ok) "\u2713  File access granted" else "File access is needed to organize your files"
        permStatus.setTextColor(if (ok) GREEN else AMBER)
        permButton.visibility = if (ok) View.GONE else View.VISIBLE
        totalView.text = prefs.getInt("total", 0).toString()
        val last = prefs.getLong("lastRun", 0L)
        lastView.text = if (last == 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last))
    }

    private fun work(label: String, block: () -> Int) {
        progress.visibility = View.VISIBLE
        runBtn.isEnabled = false; undoBtn.isEnabled = false
        logView.text = ""
        Thread {
            try { val n = block(); log("\u2713 $label finished ($n files)") }
            catch (e: Exception) { log("Error: ${e.message}") }
            runOnUiThread { progress.visibility = View.GONE; runBtn.isEnabled = true; undoBtn.isEnabled = true; refresh() }
        }.start()
    }

    private fun log(s: String) = runOnUiThread { logView.append(s + "\n") }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    // ---- UI helpers ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(CARD, 20)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun input(hintText: String, value: String, secret: Boolean) = EditText(this).apply {
        hint = hintText; setText(value); setSingleLine()
        setTextColor(TEXT); setHintTextColor(MUTED)
        background = shape(FIELD, 12)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        inputType = InputType.TYPE_CLASS_TEXT or (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else 0)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }

    private fun button(label: String, primary: Boolean) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) Color.WHITE else TEXT)
        background = if (primary)
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ACCENT, ACCENT2)).apply { cornerRadius = dp(14).toFloat() }
        else shape(FIELD, 14)
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) }
    }
}
