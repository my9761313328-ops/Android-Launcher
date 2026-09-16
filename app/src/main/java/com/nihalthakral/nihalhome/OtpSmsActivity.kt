package com.nihalthakral.nihalhome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
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
            syncNoIssuesTextSizes()
        } else {
            populateSmsAppsList(result)
            findViewById<LinearLayout>(R.id.containerSmsNoIssues).visibility = View.GONE
            findViewById<ScrollView>(R.id.scrollSmsApps).visibility = View.VISIBLE
            findViewById<TextView>(R.id.textOtpSmsSubtitle).text =
                getString(R.string.otp_sms_scan_subtitle_done, result.flaggedApps.size)
        }
    }

    private fun syncNoIssuesTextSizes() {
        val headerSubtitle = findViewById<TextView>(R.id.textOtpSmsSubtitle)
        headerSubtitle.post {
            val measuredSize = headerSubtitle.textSize
            findViewById<TextView>(R.id.textSmsNoIssuesTitle)
                .setTextSize(TypedValue.COMPLEX_UNIT_PX, measuredSize)
            findViewById<TextView>(R.id.textSmsNoIssuesSubtitle)
                .setTextSize(TypedValue.COMPLEX_UNIT_PX, measuredSize * 0.8f)
        }
    }

    private fun populateSmsAppsList(result: SmsScanResult) {
        val contentContainer = findViewById<FrameLayout>(R.id.containerOtpSmsContent)
        val container = findViewById<LinearLayout>(R.id.containerSmsApps)
        container.removeAllViews()

        contentContainer.post {
            val referenceWidth = contentContainer.width
            val referenceHeight = contentContainer.height

            val topMargin = (referenceHeight * 0.10f).toInt()
            val cardHeight = (referenceHeight * 0.12f).toInt()
            val cardGap = (referenceHeight * 0.05f).toInt()
            val sideMargin = (referenceWidth * 0.07f).toInt()

            container.setPadding(0, topMargin, 0, 0)

            val inflater = LayoutInflater.from(this)

            result.flaggedApps.forEachIndexed { index, app ->
                val row = inflater.inflate(R.layout.item_sms_app, container, false)

                row.findViewById<ImageView>(R.id.imageSmsAppIcon).setImageDrawable(app.icon)
                row.findViewById<TextView>(R.id.textSmsAppName).text = app.label
                row.findViewById<TextView>(R.id.textSmsAppPackage).text = app.packageName

                row.findViewById<View>(R.id.buttonAppInfo).setOnClickListener {
                    openAppInfoSettings(app.packageName)
                }

                val params = row.layoutParams as LinearLayout.LayoutParams
                params.height = cardHeight
                params.marginStart = sideMargin
                params.marginEnd = sideMargin
                params.bottomMargin = if (index == result.flaggedApps.lastIndex) 0 else cardGap
                row.layoutParams = params

                container.addView(row)
            }
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
