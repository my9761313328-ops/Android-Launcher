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
import android.view.ViewTreeObserver
import android.widget.Button
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
        findViewById<TextView>(R.id.textOtpSmsSubtitle).text = if (LocalizationHelper.isHindiSelected(this))
            getString(R.string.otp_sms_scan_subtitle_running_hi)
        else
            getString(R.string.otp_sms_scan_subtitle_running)
    }

    private fun showScanResult(result: SmsScanResult) {
        findViewById<ProgressBar>(R.id.progressSmsScan).visibility = View.GONE

        val isHindi = LocalizationHelper.isHindiSelected(this)

        if (result.flaggedApps.isEmpty()) {
            findViewById<ScrollView>(R.id.scrollSmsApps).visibility = View.GONE
            findViewById<LinearLayout>(R.id.containerSmsNoIssues).visibility = View.VISIBLE
            findViewById<TextView>(R.id.textOtpSmsSubtitle).text = if (isHindi)
                getString(R.string.otp_sms_scan_subtitle_done_hi, 0)
            else
                getString(R.string.otp_sms_scan_subtitle_done, 0)
            if (isHindi) {
                findViewById<TextView>(R.id.textSmsNoIssuesTitle).text =
                    getString(R.string.otp_sms_no_issues_title_hi)
                findViewById<TextView>(R.id.textSmsNoIssuesSubtitle).text =
                    getString(R.string.otp_sms_no_issues_subtitle_hi)
            }
            syncNoIssuesTextSizes()
        } else {
            populateSmsAppsList(result)
            findViewById<LinearLayout>(R.id.containerSmsNoIssues).visibility = View.GONE
            findViewById<ScrollView>(R.id.scrollSmsApps).visibility = View.VISIBLE
            findViewById<TextView>(R.id.textOtpSmsSubtitle).text = if (isHindi)
                getString(R.string.otp_sms_scan_subtitle_done_hi, result.flaggedApps.size)
            else
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
            val appInfoButtons = mutableListOf<Button>()
            val appNameViews = mutableListOf<TextView>()
            val appPackageViews = mutableListOf<TextView>()

            result.flaggedApps.forEachIndexed { index, app ->
                val row = inflater.inflate(R.layout.item_sms_app, container, false)

                row.findViewById<ImageView>(R.id.imageSmsAppIcon).setImageDrawable(app.icon)

                val nameView = row.findViewById<TextView>(R.id.textSmsAppName)
                nameView.text = app.label
                appNameViews.add(nameView)

                val packageView = row.findViewById<TextView>(R.id.textSmsAppPackage)
                packageView.text = app.packageName
                appPackageViews.add(packageView)

                val appInfoButton = row.findViewById<Button>(R.id.buttonAppInfo)
                appInfoButton.setOnClickListener {
                    openAppInfoSettings(app.packageName)
                }
                appInfoButtons.add(appInfoButton)

                val params = row.layoutParams as LinearLayout.LayoutParams
                params.height = cardHeight
                params.marginStart = sideMargin
                params.marginEnd = sideMargin
                params.bottomMargin = if (index == result.flaggedApps.lastIndex) 0 else cardGap
                row.layoutParams = params

                container.addView(row)
            }

            applyResponsiveButtonTextSize(appInfoButtons, appNameViews, appPackageViews)
        }
    }

    private fun applyResponsiveButtonTextSize(
        buttons: List<Button>,
        appNameViews: List<TextView>,
        appPackageViews: List<TextView>
    ) {
        if (buttons.isEmpty()) return
        val root = buttons[0].rootView
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val minHeight = buttons.minOf { it.height }
                if (minHeight <= 0) return

                root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                var textSizePx = minHeight * 0.42f

                buttons.forEach { button ->
                    val availableWidth =
                        (button.width - button.paddingLeft - button.paddingRight).toFloat()
                    if (availableWidth <= 0f) return@forEach

                    val paint = button.paint
                    var size = textSizePx
                    paint.textSize = size
                    while (paint.measureText(button.text.toString()) > availableWidth && size > 1f) {
                        size -= 1f
                        paint.textSize = size
                    }
                    if (size < textSizePx) textSizePx = size
                }

                buttons.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx) }

                buttons.forEach { button -> centerTextVertically(button) }

                appNameViews.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx) }

                val packageTextSizePx = textSizePx - (textSizePx * 0.30f)
                appPackageViews.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, packageTextSizePx) }
            }
        })
    }

    private fun centerTextVertically(button: Button) {
        val text = button.text?.toString().orEmpty()
        if (text.isEmpty()) return

        val paint = android.text.TextPaint(button.paint)
        val fm = paint.fontMetrics

        val width = kotlin.math.ceil(paint.measureText(text)).toInt().coerceAtLeast(1)
        val top = kotlin.math.floor(fm.top).toInt()
        val bottom = kotlin.math.ceil(fm.bottom).toInt()
        val height = (bottom - top).coerceAtLeast(1)

        val bitmap = android.graphics.Bitmap.createBitmap(
            width, height, android.graphics.Bitmap.Config.ALPHA_8
        )
        val canvas = android.graphics.Canvas(bitmap)
        val baselineY = -top.toFloat()
        canvas.drawText(text, 0f, baselineY, paint)

        var inkTop = -1
        var inkBottom = -1
        val row = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            if (row.any { (it ushr 24) != 0 }) {
                if (inkTop == -1) inkTop = y
                inkBottom = y
            }
        }
        bitmap.recycle()
        if (inkTop == -1) return

        val inkCenter = (inkTop + inkBottom) / 2f + top

        val fontMetricCenter = (fm.ascent + fm.descent) / 2f
        val shiftDown = fontMetricCenter - inkCenter

        val left = button.paddingLeft
        val right = button.paddingRight
        if (shiftDown > 0f) {
            button.setPadding(left, (shiftDown * 2f).toInt(), right, 0)
        } else {
            button.setPadding(left, 0, right, (-shiftDown * 2f).toInt())
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
