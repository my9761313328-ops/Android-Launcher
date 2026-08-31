package com.nihalthakral.nihalhome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class TestAdsActivity : ComponentActivity() {

    companion object {
        private const val TAG = "TestAdsActivity"
        private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-5939817111566865/3666119089"
        private const val BANNER_AD_UNIT_ID = "ca-app-pub-5939817111566865/8056895430"
        private const val REWARDED_AD_UNIT_ID = "ca-app-pub-5939817111566865/6100710731"
    }

    private lateinit var appOpenContainer: FrameLayout
    private lateinit var bannerContainer: FrameLayout
    private lateinit var rewardErrorContainer: FrameLayout
    private lateinit var watchRewardButton: Button

    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_ads)

        appOpenContainer = findViewById(R.id.appOpenContainer)
        bannerContainer = findViewById(R.id.bannerContainer)
        rewardErrorContainer = findViewById(R.id.rewardErrorContainer)
        watchRewardButton = findViewById(R.id.watchRewardButton)

        findViewById<Button>(R.id.closeTestingButton).setOnClickListener { finish() }

        loadAppOpenAd()
        loadBannerAd()
        setupRewardedAd()
    }

    // ---------------- App Open Ad ----------------

    private fun loadAppOpenAd() {
        AppOpenAd.load(
            this,
            APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            showError(appOpenContainer, "App Open show failed. Code ${adError.code}: ${adError.message}")
                        }
                    }
                    showAppOpenStatus("App Open Ad loaded successfully. Showing now...")
                    ad.show(this@TestAdsActivity)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    val message = "Code ${adError.code}: ${adError.message} (domain: ${adError.domain})"
                    Log.e(TAG, "App Open ad failed: $message")
                    showError(appOpenContainer, message)
                }
            }
        )
    }

    private fun showAppOpenStatus(message: String) {
        appOpenContainer.removeAllViews()
        val text = TextView(this)
        text.text = message
        text.setPadding(24, 24, 24, 24)
        text.setTextColor(resources.getColor(R.color.text_secondary, theme))
        appOpenContainer.addView(text)
    }

    // ---------------- Banner Ad ----------------

    private fun loadBannerAd() {
        val adView = AdView(this)
        adView.adUnitId = BANNER_AD_UNIT_ID
        adView.setAdSize(AdSize.BANNER)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Ad displayed automatically inside the AdView
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                val message = "Code ${adError.code}: ${adError.message} (domain: ${adError.domain})"
                Log.e(TAG, "Banner ad failed: $message")
                showError(bannerContainer, message)
            }
        }
        bannerContainer.removeAllViews()
        bannerContainer.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }

    // ---------------- Rewarded Ad ----------------

    private fun setupRewardedAd() {
        watchRewardButton.setOnClickListener {
            val ad = rewardedAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        showError(rewardErrorContainer, "Rewarded show failed. Code ${adError.code}: ${adError.message}")
                    }

                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        loadRewardedAd()
                    }
                }
                ad.show(this) { rewardItem ->
                    Toast.makeText(
                        this,
                        "Reward earned: ${rewardItem.amount} ${rewardItem.type}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                loadRewardedAd()
            }
        }
        loadRewardedAd()
    }

    private fun loadRewardedAd() {
        RewardedAd.load(
            this,
            REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    rewardErrorContainer.removeAllViews()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    val message = "Code ${adError.code}: ${adError.message} (domain: ${adError.domain})"
                    Log.e(TAG, "Rewarded ad failed: $message")
                    showError(rewardErrorContainer, message)
                }
            }
        )
    }

    // ---------------- Shared error card ----------------

    private fun showError(container: FrameLayout, errorMessage: String) {
        container.removeAllViews()
        val view = LayoutInflater.from(this)
            .inflate(R.layout.native_ad_error_layout, container, false)

        view.findViewById<TextView>(R.id.adErrorHeadline).text = "Something Error"

        view.findViewById<Button>(R.id.adErrorCopyButton).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Ad Error", errorMessage)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        container.addView(view)
    }
}
