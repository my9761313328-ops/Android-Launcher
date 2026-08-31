package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.facebook.shimmer.ShimmerFrameLayout

class NativeAdManager(
    private val appContext: Context,
    private val container: FrameLayout,
    private val onDisplayChanged: () -> Unit = {}
) {

    private var cachedNativeAd: NativeAd? = null
    private var cacheTime: Long = 0L
    private var lastAdShownTime: Long = 0L
    private var lastRequestTime: Long = 0L

    private var displayedNativeAd: NativeAd? = null

    private var shimmerView: ShimmerFrameLayout? = null

    private var isFetchInFlight = false
    private var isDestroyed = false
    private var lastErrorMessage: String? = null

    companion object {
        private const val TAG = "NativeAdManager"

        private const val NATIVE_AD_UNIT_ID = "ca-app-pub-5939817111566865/3197175798"

        private const val COOLDOWN_MS = 67_000L
        private const val CACHE_EXPIRY_MS = 40L * 60L * 1000L
        private const val OFFLINE_FALLBACK_TAG = "offline_fallback"
        private const val SHIMMER_TAG = "shimmer_loading"
        private const val FAILED_TAG = "ad_failed"
    }

    fun refresh() {
        if (isDestroyed) return
        val now = System.currentTimeMillis()

        if (lastAdShownTime != 0L && (now - lastAdShownTime) < COOLDOWN_MS) {
            return
        }

        val cached = cachedNativeAd

        if (cached == null) {
            fetchAndShowImmediately()
            return
        }

        if ((now - cacheTime) > CACHE_EXPIRY_MS) {
            Log.d(TAG, "Cached ad expired, discarding and fetching fresh")
            cached.destroy()
            cachedNativeAd = null
            fetchAndShowImmediately()
            return
        }

        cachedNativeAd = null
        showOnScreen(cached)
        lastAdShownTime = now
        fetchIntoCache()
    }

    fun showOfflineFallback() {
        if (isDestroyed) return
        displayedNativeAd?.destroy()
        displayedNativeAd = null
        cachedNativeAd?.destroy()
        cachedNativeAd = null
        shimmerView?.stopShimmer()
        shimmerView = null
        container.removeAllViews()

        val view = LayoutInflater.from(appContext)
            .inflate(R.layout.offline_game_layout, container, false)
        view.tag = OFFLINE_FALLBACK_TAG
        view.findViewById<Button>(R.id.offlineGamePlayButton).setOnClickListener {
            val intent = Intent(appContext, OfflineGameActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(intent)
        }
        container.addView(view)
        onDisplayChanged()
    }

    fun isOfflineFallbackVisible(): Boolean {
        return container.getChildAt(0)?.tag == OFFLINE_FALLBACK_TAG
    }

    fun isShimmerVisible(): Boolean {
        return container.getChildAt(0)?.tag == SHIMMER_TAG
    }

    fun isFailedVisible(): Boolean {
        return container.getChildAt(0)?.tag == FAILED_TAG
    }

    fun getLastError(): String {
        return lastErrorMessage ?: "Unknown"
    }

    private fun fetchAndShowImmediately() {
        showShimmer()
        fetchAd(
            onLoaded = { nativeAd ->
                if (isDestroyed) {
                    nativeAd.destroy()
                } else {
                    hideShimmer()
                    showOnScreen(nativeAd)
                    lastAdShownTime = System.currentTimeMillis()

                    fetchIntoCache()
                }
            },
            onFailed = { errorMsg ->
                if (!isDestroyed) {
                    hideShimmer()
                    lastErrorMessage = errorMsg
                    container.removeAllViews()
                    val failedView = View(appContext)
                    failedView.tag = FAILED_TAG
                    failedView.layoutParams = FrameLayout.LayoutParams(0, 0)
                    container.addView(failedView)
                    onDisplayChanged()
                }
            }
        )
    }

    private fun fetchIntoCache() {
        fetchAd(
            onLoaded = { nativeAd ->
                if (isDestroyed) {
                    nativeAd.destroy()
                } else {
                    cachedNativeAd = nativeAd
                    cacheTime = System.currentTimeMillis()
                }
            },
            onFailed = { errorMsg ->
                lastErrorMessage = errorMsg
            }
        )
    }

    private fun fetchAd(onLoaded: (NativeAd) -> Unit, onFailed: (String) -> Unit) {
        if (isFetchInFlight) return

        val now = System.currentTimeMillis()
        if (lastRequestTime != 0L && (now - lastRequestTime) < COOLDOWN_MS) {
            Log.d(TAG, "Request cooldown active, skipping ad request")
            onFailed("Cooldown active")
            return
        }

        isFetchInFlight = true
        lastRequestTime = now

        val adLoader = AdLoader.Builder(appContext, NATIVE_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                isFetchInFlight = false
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isFetchInFlight = false
                    Log.e(TAG, "Native ad failed to load: ${adError.message}")
                    onFailed("${adError.code}: ${adError.message}")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_BOTTOM_LEFT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun showShimmer() {
        container.removeAllViews()
        val view = LayoutInflater.from(appContext)
            .inflate(R.layout.native_ad_shimmer_layout, container, false) as ShimmerFrameLayout
        view.tag = SHIMMER_TAG
        shimmerView = view
        container.addView(view)
        view.startShimmer()
        onDisplayChanged()
    }

    private fun hideShimmer() {
        shimmerView?.stopShimmer()
        shimmerView = null
    }

    private fun showOnScreen(nativeAd: NativeAd) {

        val previous = displayedNativeAd
        displayedNativeAd = nativeAd
        container.removeAllViews()
        previous?.destroy()

        val adView = LayoutInflater.from(appContext)
            .inflate(R.layout.native_ad_layout, container, false) as NativeAdView
        (adView.layoutParams as? FrameLayout.LayoutParams)?.gravity = android.view.Gravity.CENTER

        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)

        if (!nativeAd.headline.isNullOrEmpty()) {
            headlineView.text = nativeAd.headline
            headlineView.visibility = View.VISIBLE
        } else {
            headlineView.visibility = View.INVISIBLE
        }
        adView.headlineView = headlineView

        val icon = nativeAd.icon
        if (icon?.drawable != null) {
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.INVISIBLE
        }
        adView.iconView = iconView

        if (!nativeAd.callToAction.isNullOrEmpty()) {
            ctaView.text = nativeAd.callToAction
            ctaView.visibility = View.VISIBLE
        } else {
            ctaView.visibility = View.INVISIBLE
        }
        adView.callToActionView = ctaView

        adView.setNativeAd(nativeAd)

        container.addView(adView)
        onDisplayChanged()
    }

    fun destroy() {
        isDestroyed = true
        displayedNativeAd?.destroy()
        displayedNativeAd = null
        cachedNativeAd?.destroy()
        cachedNativeAd = null
        shimmerView?.stopShimmer()
        shimmerView = null
        container.removeAllViews()
    }
}
