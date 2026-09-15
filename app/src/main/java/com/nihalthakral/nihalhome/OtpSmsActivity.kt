package com.nihalthakral.nihalhome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlin.concurrent.thread

class OtpSmsActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanRequestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_sms)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToMainScreen()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        startSmsScan()
    }

    override fun onPause() {
        super.onPause()
        scanRequestId++
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun startSmsScan() {
        val requestId = ++scanRequestId
        val startTime = System.currentTimeMillis()

        showLoadingState()

        thread {
            val result = try {
                SmsPermissionScanner.performScan(applicationContext)
            } catch (e: Exception) {
                SmsScanResult(flaggedApps = emptyList())
            }

            val elapsed = System.currentTimeMillis() - startTime
            val remainingDelay = (MIN_SPINNER_DURATION_MS - elapsed).coerceAtLeast(0L)

            mainHandler.postDelayed({
                if (requestId != scanRequestId || isFinishing) return@postDelayed
                showScanResult(result)
            }, remainingDelay)
        }
    }

    private fun showLoadingState() {
        findViewById<ProgressBar>(R.id.progressSmsScan).visibility = View.VISIBLE
        findViewById<ScrollView>(R.id.scrollSmsApps).visibility = View.GONE
        findViewById<LinearLayout>(R.id.containerSmsNoIssues).visibility = View.GONE
        findViewById<TextView>(R.id.textOtpSmsSubtitle).text =
            getString(R.string.otp_sms_scan_subtitle_running)
    }

    private fun showScanResult(result: SmsScanResult) {
        findViewById<ProgressBar>(R.id.progressSmsScan).visibility = View.GONE

        if (result.flaggedApps.isEmpty()) {
            findViewById<ScrollView>(R.id.scrollSmsApps).visibility = View.GONE
            findViewById<LinearLayout>(R.id.containerSmsNoIssues).visibility = View.VISIBLE
            findViewById<TextView>(R.id.textOtpSmsSubtitle).text =
                getString(R.string.otp_sms_scan_subtitle_done, 0)
        } else {
            populateSmsAppsList(result)
            findViewById<LinearLayout>(R.id.containerSmsNoIssues).visibility = View.GONE
            findViewById<ScrollView>(R.id.scrollSmsApps).visibility = View.VISIBLE
            findViewById<TextView>(R.id.textOtpSmsSubtitle).text =
                getString(R.string.otp_sms_scan_subtitle_done, result.flaggedApps.size)
        }
    }

    private fun populateSmsAppsList(result: SmsScanResult) {
        val container = findViewById<LinearLayout>(R.id.containerSmsApps)
        container.removeAllViews()

        val inflater = LayoutInflater.from(this)

        result.flaggedApps.forEach { app ->
            val row = inflater.inflate(R.layout.item_sms_app, container, false)

            row.findViewById<ImageView>(R.id.imageSmsAppIcon).setImageDrawable(app.icon)
            row.findViewById<TextView>(R.id.textSmsAppName).text = app.label
            row.findViewById<TextView>(R.id.textSmsAppPackage).text = app.packageName

            row.findViewById<View>(R.id.buttonAppInfo).setOnClickListener {
                openAppInfoSettings(app.packageName)
            }

            container.addView(row)
        }
    }

    private fun openAppInfoSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
        }
    }

    private fun goToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val MIN_SPINNER_DURATION_MS = 500L
    }
}
