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

        applyResponsiveButtonTextSize(findViewById(R.id.buttonGo))

        findViewById<View>(R.id.cardUninstallApps).setOnClickListener {
            startActivity(Intent(this, UninstallAppsActivity::class.java))
        }

        findViewById<View>(R.id.cardOtpSms).setOnClickListener {
            startActivity(Intent(this, OtpSmsActivity::class.java))
        }

        findViewById<View>(R.id.cardSocialScams).setOnClickListener {
            startActivity(Intent(this, SocialScamsActivity::class.java))
        }

        findViewById<View>(R.id.cardSeeMore).setOnClickListener {
            startActivity(Intent(this, SeeMoreActivity::class.java))
        }

        findViewById<View>(R.id.containerAskNihalAi).setOnClickListener {
            startActivity(Intent(this, AskNihalAiActivity::class.java))
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

    /**
     * The button's height/width is fixed in dp (see activity_main.xml), so
     * its pixel size already varies per device DPI. Once the button is
     * actually laid out, this measures its real pixel height and derives a
     * text size (a fixed fraction of that height) applied to it - this
     * keeps the label visually proportional to the button on every screen,
     * instead of relying on the platform's default text size which can
     * look inconsistent across DPIs.
     */
    private fun applyResponsiveButtonTextSize(vararg buttons: Button) {
        val root = buttons[0].rootView
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val minHeight = buttons.minOf { it.height }
                if (minHeight <= 0) return // layout not measured yet

                root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // A label ~42% of the button's own height reads as a
                // comfortably filled button without touching its edges.
                var textSizePx = minHeight * 0.42f

                // Safety net: if the label would not fit the button's
                // width, shrink the size just enough for it to fit.
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

                // Gravity="center" centers text using the FONT's ascent/
                // descent metrics, not the actual drawn pixels of the
                // specific label. Words with no descenders don't use the
                // space the font reserves below the baseline, so they
                // visually sit a little above true center. This nudges
                // each label using its own real ink bounds so it lands
                // dead center.
                buttons.forEach { button -> centerTextVertically(button) }
            }
        })
    }

    /**
     * Renders the button's own label onto a small offscreen bitmap (with
     * the exact same paint - size, typeface, everything) and scans it to
     * find the topmost and bottommost row that actually has ink. This is
     * pixel-exact: it reflects precisely what will be drawn on screen, so
     * it centers correctly for ANY text, regardless of whether the font's
     * reported ascent/descent metrics happen to match that word's real
     * shape.
     */
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
        val baselineY = -top.toFloat() // baseline sits 'top' px below the bitmap's top edge
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
        if (inkTop == -1) return // nothing drawn (blank text) - nothing to center

        // Real ink's vertical midpoint, relative to the baseline.
        val inkCenter = (inkTop + inkBottom) / 2f + top

        // Where gravity="center" currently anchors the line, relative to
        // the baseline, using font metrics (since includeFontPadding is
        // "false").
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
}
