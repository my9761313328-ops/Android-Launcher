package com.nihalthakral.nihalhome

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class PremiumActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var rewardedAd: RewardedAd? = null
    private var isAdBusy = false
    private var rewardEarned = false
    private lateinit var buttonWatchAdRef: Button

    private val fallbackAdLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isAdBusy = false
        if (result.resultCode == Activity.RESULT_OK) {
            rewardEarned = true
            onRewardEarned()
        } else {
            buttonWatchAdRef.text = getString(R.string.action_watch_ad)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        MobileAds.initialize(this) {}

        val buttonLocked = findViewById<Button>(R.id.buttonLocked)
        val buttonWatchAd = findViewById<Button>(R.id.buttonWatchAd)
        val buttonSkip = findViewById<Button>(R.id.buttonSkip)
        val skipCountdownBadge = findViewById<TextView>(R.id.skipCountdownBadge)
        val emojiWatchAd = findViewById<TextView>(R.id.emojiWatchAd)
        val watchAdContainer = findViewById<FrameLayout>(R.id.watchAdContainer)
        emojiWatchAd.bringToFront()
        buttonWatchAdRef = buttonWatchAd

        if (LocalizationHelper.isHindiSelected(this)) {
            buttonLocked.text = getString(R.string.action_locked_hi)
            findViewById<TextView>(R.id.textPremiumLabel).text = getString(R.string.premium_label_hi)
            findViewById<TextView>(R.id.textStepWatchTitle).text = getString(R.string.premium_step_watch_title_hi)
            findViewById<TextView>(R.id.textStepWatchSub).text = getString(R.string.premium_step_watch_sub_hi)
            findViewById<TextView>(R.id.textStepUnlockTitle).text = getString(R.string.premium_step_unlock_title_hi)
            findViewById<TextView>(R.id.textStepUnlockSub).text = getString(R.string.premium_step_unlock_sub_hi)
            findViewById<TextView>(R.id.textStepRepeatTitle).text = getString(R.string.premium_step_repeat_title_hi)
            findViewById<TextView>(R.id.textStepRepeatSub).text = getString(R.string.premium_step_repeat_sub_hi)
        }

        buttonLocked.setOnClickListener {
            vibrateDevice()
            shakeView(buttonLocked)
        }

        buttonWatchAd.setOnClickListener {
            onWatchAdClicked()
        }

        startPulseAnimation(watchAdContainer)
        startSkipCountdown(buttonSkip, skipCountdownBadge)

        applyResponsiveButtonTextSize(buttonLocked) { lockedTextSizePx ->
            emojiWatchAd.setTextSize(TypedValue.COMPLEX_UNIT_PX, lockedTextSizePx)
        }
        applyResponsiveButtonTextSize(buttonWatchAd)
        applyResponsiveButtonTextSize(buttonSkip)

        applyResponsiveStepsStrip(buttonLocked)
    }

    private fun applyResponsiveStepsStrip(buttonLocked: Button) {
        val stepsStripContainer = findViewById<LinearLayout>(R.id.stepsStripContainer)
        val root = buttonLocked.rootView
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val lockedHeight = buttonLocked.height
                if (lockedHeight <= 0) return

                root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val containerHeightPx = lockedHeight * 2
                val params = stepsStripContainer.layoutParams
                params.height = containerHeightPx
                stepsStripContainer.layoutParams = params

                val iconSizePx = (containerHeightPx * 0.34f).toInt()
                val innerIconPx = (iconSizePx * 0.5f).toInt()
                val titleTextPx = iconSizePx * 0.30f
                val subTextPx = titleTextPx * 0.833f
                val lineThicknessPx = (iconSizePx * 0.10f).toInt().coerceAtLeast(1)
                val chevronWidthPx = (iconSizePx * 0.20f).toInt().coerceAtLeast(1)
                val chevronHeightPx = (iconSizePx * 0.30f).toInt().coerceAtLeast(1)
                val gapAboveTitlePx = (iconSizePx * 0.20f).toInt()
                val gapAboveSubPx = (iconSizePx * 0.05f).toInt()

                resizeSquare(findViewById(R.id.iconStepWatch), iconSizePx)
                resizeSquare(findViewById(R.id.iconStepUnlock), iconSizePx)
                resizeSquare(findViewById(R.id.iconStepRepeat), iconSizePx)

                resizeSquare(findViewById(R.id.imgStepWatch), innerIconPx)
                resizeSquare(findViewById(R.id.imgStepUnlock), innerIconPx)
                resizeSquare(findViewById(R.id.imgStepRepeat), innerIconPx)

                resizeExact(findViewById(R.id.chevronStepUnlock), chevronWidthPx, chevronHeightPx)
                resizeExact(findViewById(R.id.chevronStepRepeat), chevronWidthPx, chevronHeightPx)

                setLineThickness(findViewById(R.id.lineWatchLeft), lineThicknessPx)
                setLineThickness(findViewById(R.id.lineWatchRight), lineThicknessPx)
                setLineThickness(findViewById(R.id.lineUnlockLeft), lineThicknessPx)
                setLineThickness(findViewById(R.id.lineUnlockRight), lineThicknessPx)
                setLineThickness(findViewById(R.id.lineRepeatLeft), lineThicknessPx)
                setLineThickness(findViewById(R.id.lineRepeatRight), lineThicknessPx)

                val titleIds = intArrayOf(R.id.textStepWatchTitle, R.id.textStepUnlockTitle, R.id.textStepRepeatTitle)
                val subIds = intArrayOf(R.id.textStepWatchSub, R.id.textStepUnlockSub, R.id.textStepRepeatSub)

                titleIds.forEach { id ->
                    val textView = findViewById<TextView>(id)
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleTextPx)
                    setTopMargin(textView, gapAboveTitlePx)
                }

                subIds.forEach { id ->
                    val textView = findViewById<TextView>(id)
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, subTextPx)
                    setTopMargin(textView, gapAboveSubPx)
                }
            }
        })
    }

    private fun resizeSquare(view: View, sizePx: Int) {
        val params = view.layoutParams
        params.width = sizePx
        params.height = sizePx
        view.layoutParams = params
    }

    private fun resizeExact(view: View, widthPx: Int, heightPx: Int) {
        val params = view.layoutParams
        params.width = widthPx
        params.height = heightPx
        view.layoutParams = params
    }

    private fun setLineThickness(view: View, thicknessPx: Int) {
        val params = view.layoutParams
        params.height = thicknessPx
        view.layoutParams = params
    }

    private fun setTopMargin(view: View, marginPx: Int) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.topMargin = marginPx
        view.layoutParams = params
    }

    private fun onWatchAdClicked() {
        if (isAdBusy) return
        isAdBusy = true
        rewardEarned = false
        buttonWatchAdRef.text = getString(R.string.action_loading_ad)

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            this,
            TEST_REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    showRewardedAd(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    if (adError.code == AdRequest.ERROR_CODE_NO_FILL) {
                        showFallbackAd()
                    } else {
                        isAdBusy = false
                        buttonWatchAdRef.text = getString(R.string.action_retry_ad)
                    }
                }
            }
        )
    }

    private fun showFallbackAd() {
        fallbackAdLauncher.launch(Intent(this, FallbackAdActivity::class.java))
    }

    private fun showRewardedAd(ad: RewardedAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                isAdBusy = false
                if (!rewardEarned) {
                    buttonWatchAdRef.text = getString(R.string.action_watch_ad)
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                isAdBusy = false
                buttonWatchAdRef.text = getString(R.string.action_retry_ad)
            }
        }

        ad.show(this) {
            rewardEarned = true
            onRewardEarned()
        }
    }

    private fun onRewardEarned() {
        val prefs = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putLong(
            PreferenceKeys.KEY_UNLOCK_EXPIRY_TIMESTAMP,
            System.currentTimeMillis() + PreferenceKeys.UNLOCK_DURATION_MS
        ).apply()
        goToMainScreen()
    }

    private fun goToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun startSkipCountdown(buttonSkip: Button, countdownBadge: TextView) {
        var remaining = SKIP_COUNTDOWN_SECONDS
        countdownBadge.text = remaining.toString()

        val tick = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining <= 0) {
                    countdownBadge.visibility = View.GONE
                    buttonSkip.isEnabled = true
                    buttonSkip.setOnClickListener {
                        goToFavouriteLauncherOrChooser()
                    }
                } else {
                    countdownBadge.text = remaining.toString()
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        mainHandler.postDelayed(tick, 1000L)
    }

    private fun goToFavouriteLauncherOrChooser() {
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

    private fun vibrateDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(200)
        }
    }

    private fun shakeView(view: View) {
        val animator = ObjectAnimator.ofFloat(
            view, "translationX", 0f, -20f, 20f, -16f, 16f, -8f, 8f, 0f
        )
        animator.duration = 400
        animator.start()
    }

    private fun startPulseAnimation(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.06f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.06f, 1f)
        scaleX.repeatCount = ObjectAnimator.INFINITE
        scaleY.repeatCount = ObjectAnimator.INFINITE
        scaleX.duration = 900
        scaleY.duration = 900
        scaleX.start()
        scaleY.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun applyResponsiveButtonTextSize(
        vararg buttons: Button,
        onSized: ((Float) -> Unit)? = null
    ) {
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

                onSized?.invoke(textSizePx)
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

    companion object {
        private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-5939817111566865/1834673417"
        private const val SKIP_COUNTDOWN_SECONDS = 30
    }
}
