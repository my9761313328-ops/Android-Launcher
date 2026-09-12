package com.nihalthakral.nihalhome

import android.content.Intent
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

        if (isUnlockExpired(prefs)) {
            startActivity(Intent(this, PremiumActivity::class.java))
            finish()
            return
        }

        ensureUnlockInitialized(prefs)

        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        findViewById<Button>(R.id.buttonClose).setOnClickListener {
            onCloseClicked()
        }

        applyResponsiveButtonTextSize(findViewById(R.id.buttonClose))

        applyResponsivePillContentSize(
            container = findViewById(R.id.containerAskAnExpert),
            icon = findViewById(R.id.imageAskAnExpertIcon),
            text = findViewById(R.id.textAskAnExpert)
        )

        findViewById<View>(R.id.cardUninstallApps).setOnClickListener {
            startActivity(Intent(this, UninstallAppsActivity::class.java))
        }

        findViewById<View>(R.id.cardOtpSms).setOnClickListener {
            startActivity(Intent(this, OtpSmsActivity::class.java))
        }

        findViewById<View>(R.id.cardSocialHacking).setOnClickListener {
            startActivity(Intent(this, SocialHackingActivity::class.java))
        }

        findViewById<View>(R.id.cardSeeMore).setOnClickListener {
            startActivity(Intent(this, SeeMoreActivity::class.java))
        }

        findViewById<View>(R.id.containerAskAnExpert).setOnClickListener {
            startActivity(Intent(this, AskAnExpertActivity::class.java))
        }

        applyHeaderSizeToFeatureCardsDeferred()

        isMainContentReady = true
    }

    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)
        if (isMainContentReady && isUnlockExpired(prefs)) {
            startActivity(Intent(this, PremiumActivity::class.java))
            finish()
            return
        }

        if (isMainContentReady) {
            startAccessibilityScan()
        }
    }

    private fun isUnlockExpired(prefs: android.content.SharedPreferences): Boolean {
        val expiry = prefs.getLong(PreferenceKeys.KEY_UNLOCK_EXPIRY_TIMESTAMP, 0L)
        if (expiry == 0L) return false
        return System.currentTimeMillis() >= expiry
    }

    private fun ensureUnlockInitialized(prefs: android.content.SharedPreferences) {
        val expiry = prefs.getLong(PreferenceKeys.KEY_UNLOCK_EXPIRY_TIMESTAMP, 0L)
        if (expiry == 0L) {
            prefs.edit().putLong(
                PreferenceKeys.KEY_UNLOCK_EXPIRY_TIMESTAMP,
                System.currentTimeMillis() + PreferenceKeys.UNLOCK_DURATION_MS
            ).apply()
        }
    }

    private fun startAccessibilityScan() {

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

    private fun resetScanUi() {
        findViewById<TextView>(R.id.textTerminalLog).text = ""
        findViewById<TextView>(R.id.textScanSubtitle).text = getString(R.string.scan_subtitle_running)

        findViewById<View>(R.id.wrapperResults).visibility = View.GONE
        findViewById<View>(R.id.containerNoIssues).visibility = View.GONE
        findViewById<View>(R.id.wrapperTerminal).visibility = View.VISIBLE
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

        applyHeaderSizeToFeatureCardsDeferred()
    }

    private fun showNoIssuesFound(scannedCount: Int) {
        findViewById<View>(R.id.wrapperTerminal).visibility = View.GONE
        findViewById<View>(R.id.wrapperResults).visibility = View.GONE
        findViewById<View>(R.id.containerNoIssues).visibility = View.VISIBLE

        if (scannedCount > 0) {
            findViewById<TextView>(R.id.textScanSubtitle).text =
                getString(R.string.scan_subtitle_done, scannedCount)
        }

        matchNoIssuesStyleToHeader()
    }

    private fun matchNoIssuesStyleToHeader() {
        val headerIcon = findViewById<ImageView>(R.id.imageScanIcon)
        val headerTitle = findViewById<TextView>(R.id.textScanTitle)
        val headerSubtitle = findViewById<TextView>(R.id.textScanSubtitle)

        val noIssuesIcon = findViewById<ImageView>(R.id.imageNoIssuesIcon)
        val noIssuesTitle = findViewById<TextView>(R.id.textNoIssuesTitle)
        val noIssuesSubtitle = findViewById<TextView>(R.id.textNoIssuesSubtitle)

        headerIcon.post {
            if (headerIcon.width > 0 && headerIcon.height > 0) {
                val squareSize = minOf(headerIcon.width, headerIcon.height)
                val params = noIssuesIcon.layoutParams
                params.width = squareSize
                params.height = squareSize
                noIssuesIcon.layoutParams = params
            }

            if (headerTitle.textSize > 0f) {
                noIssuesTitle.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                noIssuesTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, headerTitle.textSize)
            }

            if (headerSubtitle.textSize > 0f) {
                noIssuesSubtitle.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                noIssuesSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, headerSubtitle.textSize)
            }
        }
    }

    private fun applyHeaderSizeToFeatureCardsDeferred() {
        val headerIcon = findViewById<ImageView>(R.id.imageScanIcon)
        val headerTitle = findViewById<TextView>(R.id.textScanTitle)
        val headerSubtitle = findViewById<TextView>(R.id.textScanSubtitle)

        headerIcon.post {
            applyHeaderSizeToFeatureCards(headerIcon, headerTitle, headerSubtitle)
        }
    }

    private fun applyHeaderSizeToFeatureCards(
        headerIcon: ImageView,
        headerTitle: TextView,
        headerSubtitle: TextView
    ) {
        val cardIconIds = intArrayOf(
            R.id.imageFeatureIcon1,
            R.id.imageFeatureIcon2,
            R.id.imageFeatureIcon3,
            R.id.imageFeatureIcon4
        )
        val cardTitleIds = intArrayOf(
            R.id.textFeatureTitle1,
            R.id.textFeatureTitle2,
            R.id.textFeatureTitle3,
            R.id.textFeatureTitle4
        )
        val cardSubtitleIds = intArrayOf(
            R.id.textFeatureSubtitle1,
            R.id.textFeatureSubtitle2,
            R.id.textFeatureSubtitle3,
            R.id.textFeatureSubtitle4
        )

        val squareIconSize = if (headerIcon.width > 0 && headerIcon.height > 0) {
            minOf(headerIcon.width, headerIcon.height)
        } else {
            0
        }

        cardIconIds.forEach { id ->
            val cardIcon = findViewById<ImageView>(id)
            if (squareIconSize > 0) {
                val params = cardIcon.layoutParams
                params.width = squareIconSize
                params.height = squareIconSize
                cardIcon.layoutParams = params
            }
        }

        if (headerTitle.textSize > 0f) {
            cardTitleIds.forEach { id ->
                findViewById<TextView>(id).setTextSize(TypedValue.COMPLEX_UNIT_PX, headerTitle.textSize)
            }
        }

        if (headerSubtitle.textSize > 0f) {
            cardSubtitleIds.forEach { id ->
                findViewById<TextView>(id).setTextSize(TypedValue.COMPLEX_UNIT_PX, headerSubtitle.textSize)
            }
        }
    }

    private fun showRiskResults(result: com.nihalthakral.nihalhome.ScanResult) {
        val container = findViewById<LinearLayout>(R.id.containerRiskResults)
        container.removeAllViews()

        val scannerCard = findViewById<View>(R.id.boxFirstPart)
        val inflater = LayoutInflater.from(this)
        val itemHeight = (scannerCard.height * 0.18).toInt()
        val gapHeight  = (scannerCard.height * 0.05).toInt()

        result.flaggedApps.forEachIndexed { index, app ->
            if (index > 0 && gapHeight > 0) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    gapHeight
                )
                container.addView(spacer)
            }

            val row = inflater.inflate(R.layout.item_risk_app, container, false)

            if (itemHeight > 0) {
                row.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    itemHeight
                )
            }

            row.findViewById<ImageView>(R.id.imageRiskAppIcon).setImageDrawable(app.icon)
            row.findViewById<TextView>(R.id.textRiskAppName).text = app.label

            row.setOnClickListener {
                openAccessibilityServiceSettings(app.packageName, app.serviceClassName)
            }

            container.addView(row)
        }

        findViewById<View>(R.id.wrapperTerminal).visibility = View.GONE
        findViewById<View>(R.id.containerNoIssues).visibility = View.GONE
        findViewById<View>(R.id.wrapperResults).visibility = View.VISIBLE
    }

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

            }
        }

        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun onCloseClicked() {
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

    private fun applyResponsiveButtonTextSize(vararg buttons: Button) {
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
            }
        })
    }

    private fun applyResponsivePillContentSize(container: View, icon: ImageView, text: TextView) {
        container.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val containerHeight = container.height
                val containerWidth = container.width
                if (containerHeight <= 0 || containerWidth <= 0) return

                container.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val iconSizePx = (containerHeight * 0.5f).toInt().coerceAtLeast(1)
                val iconMarginPx = (containerHeight * 0.17f).toInt().coerceAtLeast(1)
                val iconParams = icon.layoutParams as LinearLayout.LayoutParams
                iconParams.width = iconSizePx
                iconParams.height = iconSizePx
                iconParams.marginEnd = iconMarginPx
                icon.layoutParams = iconParams

                val availableWidth = (
                    containerWidth - container.paddingLeft - container.paddingRight -
                        iconSizePx - iconMarginPx
                    ).toFloat()

                var textSizePx = containerHeight * 0.42f
                if (availableWidth > 0f) {
                    val paint = text.paint
                    var size = textSizePx
                    paint.textSize = size
                    while (paint.measureText(text.text.toString()) > availableWidth && size > 1f) {
                        size -= 1f
                        paint.textSize = size
                    }
                    textSizePx = size
                }

                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            }
        })
    }

    private fun centerTextVertically(button: Button) {
        val text = button.text?.toString().orEmpty()
        if (text.isEmpty()) return

        val paint = button.paint
        val fm = paint.fontMetrics

        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val inkCenter = (bounds.top + bounds.bottom) / 2f
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
