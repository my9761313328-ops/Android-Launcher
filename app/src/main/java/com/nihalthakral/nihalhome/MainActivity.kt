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

        startAccessibilityScan()
    }

    // ---------------------------------------------------------------
    // Accessibility scan: runs automatically, shows a terminal-style
    // feed of every app being checked, then reveals the results.
    // ---------------------------------------------------------------

    private fun startAccessibilityScan() {
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
                openAppInfo(app.packageName)
            }

            container.addView(row)
        }

        findViewById<View>(R.id.scrollTerminal).visibility = View.GONE
        findViewById<View>(R.id.containerNoIssues).visibility = View.GONE
        findViewById<View>(R.id.scrollResults).visibility = View.VISIBLE
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // No app info screen available on this device/OEM — ignore silently.
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
