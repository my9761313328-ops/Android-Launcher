package com.nihalthakral.nihalhome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    // True once setContentView + view setup has run for this Activity
    // instance. Used so onResume knows whether it's safe to run the scan
    // (it isn't, on the very first onCreate call that redirects away to
    // language selection / default launcher setup, since no layout is
    // inflated in that case).
    private var isMainContentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)
        val languageSelected = prefs.contains(PreferenceKeys.KEY_SELECTED_LANGUAGE)

        if (!languageSelected) {
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
            finish()
            return
        }

        if (!LauncherUtils.isDefaultLauncher(this)) {
            startActivity(Intent(this, DefaultLauncherActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        findViewById<Button>(R.id.buttonGo).setOnClickListener {
            onGoClicked()
        }

        isMainContentReady = true
    }

    // ---------------------------------------------------------------
    // Accessibility scan: runs automatically every time the launcher
    // comes to the foreground (fresh launch, Home button press, back
    // from another app, etc.) — never just once on first create.
    // ---------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        if (isMainContentReady) {
            startAccessibilityScan()
        }
    }

    private fun startAccessibilityScan() {
        // Cancel any in-progress scan animation from a previous visit
        // before starting a fresh one.
        mainHandler.removeCallbacksAndMessages(null)
        resetScanUi()

        val terminalLog = findViewById<TextView>(R.id.textTerminalLog)
        val subtitle = findViewById<TextView>(R.id.textScanSubtitle)

        thread {
            val result = try {
                AccessibilityScanner.performScan(applicationContext)
            } catch (e: Exception) {
                null
            }

            mainHandler.post {
                if (result == null || isFinishing) {
                    showNoIssuesFound(0)
                    return@post
                }
                animateTerminalScan(terminalLog, subtitle, result)
            }
        }
    }

    /**
     * Puts the screen back into the "scanning…" visual state — hides
     * whatever result view (No Issues / Flagged list) was showing from
     * the previous visit, clears the old terminal text, and shows the
     * terminal feed again so the scan animation can restart cleanly.
     */
    private fun resetScanUi() {
        findViewById<TextView>(R.id.textTerminalLog).text = ""
        findViewById<TextView>(R.id.textScanSubtitle).text = getString(R.string.scan_subtitle_running)

        findViewById<View>(R.id.scrollResults).visibility = View.GONE
        findViewById<View>(R.id.containerNoIssues).visibility = View.GONE
        findViewById<View>(R.id.scrollTerminal).visibility = View.VISIBLE
    }

    private fun animateTerminalScan(
        terminalLog: TextView,
        subtitle: TextView,
        result: com.nihalthakral.nihalhome.ScanResult
    ) {
        val entries = result.scannedApps
        if (entries.isEmpty()) {
            finishScan(result)
            return
        }

        val scrollTerminal = findViewById<ScrollView>(R.id.scrollTerminal)
        val totalTargetDurationMs = 1600L
        val tickIntervalMs = 16L
        val totalTicks = (totalTargetDurationMs / tickIntervalMs).toInt().coerceAtLeast(1)
        val batchSize = (entries.size / totalTicks).coerceAtLeast(1)

        var index = 0
        val builder = StringBuilder()

        val step = object : Runnable {
            override fun run() {
                if (index >= entries.size) {
                    finishScan(result)
                    return
                }

                val end = (index + batchSize).coerceAtMost(entries.size)
                for (i in index until end) {
                    builder.append("$ scanning ").append(entries[i].packageName).append('\n')
                }
                index = end

                terminalLog.text = builder.toString()
                scrollTerminal.post { scrollTerminal.fullScroll(View.FOCUS_DOWN) }

                mainHandler.postDelayed(this, tickIntervalMs)
            }
        }
        mainHandler.post(step)
    }

    private fun finishScan(result: com.nihalthakral.nihalhome.ScanResult) {
        val subtitle = findViewById<TextView>(R.id.textScanSubtitle)
        subtitle.text = getString(R.string.scan_subtitle_done, result.scannedApps.size)

        if (result.flaggedApps.isEmpty()) {
            showNoIssuesFound(result.scannedApps.size)
        } else {
            showRiskResults(result)
        }
    }

    private fun showNoIssuesFound(scannedCount: Int) {
        findViewById<View>(R.id.scrollTerminal).visibility = View.GONE
        findViewById<View>(R.id.scrollResults).visibility = View.GONE
        findViewById<View>(R.id.containerNoIssues).visibility = View.VISIBLE

        if (scannedCount > 0) {
            findViewById<TextView>(R.id.textScanSubtitle).text =
                getString(R.string.scan_subtitle_done, scannedCount)
        }
    }

    private fun showRiskResults(result: com.nihalthakral.nihalhome.ScanResult) {
        val container = findViewById<LinearLayout>(R.id.containerRiskResults)
        container.removeAllViews()

        val inflater = LayoutInflater.from(this)
        for (app in result.flaggedApps) {
            val row = inflater.inflate(R.layout.item_risk_app, container, false)

            row.findViewById<ImageView>(R.id.imageRiskAppIcon).setImageDrawable(app.icon)
            row.findViewById<TextView>(R.id.textRiskAppName).text = app.label
            row.findViewById<TextView>(R.id.textRiskAppPackage).text = app.packageName

            row.setOnClickListener {
                openAccessibilityServiceSettings(app.packageName, app.serviceClassName)
            }

            container.addView(row)
        }

        findViewById<View>(R.id.scrollTerminal).visibility = View.GONE
        findViewById<View>(R.id.containerNoIssues).visibility = View.GONE
        findViewById<View>(R.id.scrollResults).visibility = View.VISIBLE
    }

    /**
     * Opens the specific Accessibility Service's own settings screen
     * (the page with the ON/OFF toggle for that service) so the user
     * can turn it off directly, instead of opening generic App Info.
     *
     * On Android 12+ (API 31+) this deep-links straight to that service's
     * detail page. On older versions, or if the deep-link isn't supported
     * by the device's OEM, it falls back to the general Accessibility
     * Settings list where the user can find and disable it manually.
     */
    private fun openAccessibilityServiceSettings(packageName: String, serviceClassName: String) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                val componentName = android.content.ComponentName(packageName, serviceClassName)
                val detailIntent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                    putExtra("android.intent.extra.COMPONENT_NAME", componentName)
                }
                startActivity(detailIntent)
                return
            } catch (e: Exception) {
                // Fall through to the general list below.
            }
        }

        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            // No accessibility settings screen available — ignore silently.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun onGoClicked() {
        val prefs = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)
        val dontAskAgain = prefs.getBoolean(PreferenceKeys.KEY_DONT_ASK_AGAIN, false)
        val savedPackage = prefs.getString(PreferenceKeys.KEY_SAVED_LAUNCHER_PACKAGE, null)
        val savedActivity = prefs.getString(PreferenceKeys.KEY_SAVED_LAUNCHER_CLASS, null)

        if (dontAskAgain && savedPackage != null && savedActivity != null) {
            val launched = LauncherUtils.launchSelected(this, savedPackage, savedActivity)
            if (!launched) {
                startActivity(Intent(this, ChooseLauncherActivity::class.java))
            }
        } else {
            startActivity(Intent(this, ChooseLauncherActivity::class.java))
        }
    }
}
