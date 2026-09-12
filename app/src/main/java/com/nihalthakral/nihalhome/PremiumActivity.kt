package com.nihalthakral.nihalhome

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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

        buttonLocked.setOnClickListener {
            vibrateDevice()
            shakeView(buttonLocked)
        }

        buttonWatchAd.setOnClickListener {
            showRewardedAd()
        }

        startPulseAnimation(buttonWatchAd)
        startSkipCountdown(buttonSkip)
        loadRewardedAd()
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            this,
            TEST_REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    private fun showRewardedAd() {
        val ad = rewardedAd
        if (ad == null) {
            loadRewardedAd()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                loadRewardedAd()
            }
        }

        ad.show(this) {
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

    private fun startSkipCountdown(buttonSkip: Button) {
        var remaining = SKIP_COUNTDOWN_SECONDS
        buttonSkip.text = getString(R.string.action_skip_in_seconds, remaining)

        val tick = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining <= 0) {
                    buttonSkip.text = getString(R.string.action_skip)
                    buttonSkip.isEnabled = true
                    buttonSkip.setOnClickListener {
                        goToFavouriteLauncherOrChooser()
                    }
                } else {
                    buttonSkip.text = getString(R.string.action_skip_in_seconds, remaining)
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

    companion object {
        private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val SKIP_COUNTDOWN_SECONDS = 15
    }
}
