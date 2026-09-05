package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
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

    // Tracks consecutive real AdMob failures (requests that actually went
    // out and got an error back) to drive the escalating error-backoff
    // cooldown. Internal skips (e.g. our own cooldown blocking a request
    // before it's sent) never touch this.
    private var consecutiveAdErrorCount: Int = 0
    private var nextRetryAllowedAt: Long = 0L

    private var displayedNativeAd: NativeAd? = null

    private var shimmerView: ShimmerFrameLayout? = null

    private var isFetchInFlight = false
    private var isDestroyed = false
    private var lastErrorMessage: String? = null

    companion object {
        private const val TAG = "NativeAdManager"

        private const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

        private const val COOLDOWN_MS = 300_000L
        private const val CACHE_EXPIRY_MS = 40L * 60L * 1000L

        // Escalating cooldown applied only after a *real* AdMob request
        // comes back with an error (No Fill, network error, etc.) — not
        // after an internally-skipped request (e.g. our own cooldown was
        // still active). Resets to the 1st tier the moment a real ad
        // loads successfully.
        private const val ERROR_COOLDOWN_TIER_1_MS = 15L * 60L * 1000L  // 1st consecutive error
        private const val ERROR_COOLDOWN_TIER_2_MS = 30L * 60L * 1000L  // 2nd consecutive error
        private const val ERROR_COOLDOWN_TIER_3_MS = 60L * 60L * 1000L  // 3rd+ consecutive error (max)

        private const val OFFLINE_FALLBACK_TAG = "offline_fallback"
        private const val AD_FALLBACK_TAG = "ad_fallback"
        private const val SHIMMER_TAG = "shimmer_loading"
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
        showGameFallbackCard(OFFLINE_FALLBACK_TAG)
    }

    private fun showAdErrorFallback() {
        val now = System.currentTimeMillis()
        val cached = cachedNativeAd

        if (cached != null && (now - cacheTime) <= CACHE_EXPIRY_MS) {
            // We still have a good, non-expired cached ad — show that
            // instead of dropping to the offline game card. The user
            // never notices the request that just failed in the
            // background.
            cachedNativeAd = null
            hideShimmer()
            showOnScreen(cached)
            lastAdShownTime = now
            return
        }

        // No usable cache — this is the only case where the offline
        // game card is shown for an ad error.
        showGameFallbackCard(AD_FALLBACK_TAG)
    }

    private fun showGameFallbackCard(tag: String) {
        if (isDestroyed) return
        displayedNativeAd?.destroy()
        displayedNativeAd = null
        // Note: the cached ad (cachedNativeAd) is intentionally left
        // untouched here. It should only ever be destroyed when it
        // actually expires (see refresh()) — never just because we're
        // showing a fallback card due to an error or no internet.
        shimmerView?.stopShimmer()
        shimmerView = null
        container.removeAllViews()

        val view = LayoutInflater.from(appContext)
            .inflate(R.layout.offline_game_layout, container, false)
        view.tag = tag
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

    fun isAdFallbackVisible(): Boolean {
        return container.getChildAt(0)?.tag == AD_FALLBACK_TAG
    }

    fun isShimmerVisible(): Boolean {
        return container.getChildAt(0)?.tag == SHIMMER_TAG
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
                    showAdErrorFallback()
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

        // Escalating cooldown from previous real errors — skipping here
        // means no request goes out at all, so this does NOT count as
        // another consecutive error.
        if (now < nextRetryAllowedAt) {
            Log.d(TAG, "Error backoff active, skipping ad request")
            onFailed("Cooldown active")
            return
        }

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
                // Real success — clear any error backoff entirely.
                consecutiveAdErrorCount = 0
                nextRetryAllowedAt = 0L
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isFetchInFlight = false
                    Log.e(TAG, "Native ad failed to load: ${adError.message}")

                    // This was a real request that Google responded to
                    // with an error, so it counts toward the escalating
                    // backoff.
                    consecutiveAdErrorCount += 1
                    val backoffMs = when {
                        consecutiveAdErrorCount <= 1 -> ERROR_COOLDOWN_TIER_1_MS
                        consecutiveAdErrorCount == 2 -> ERROR_COOLDOWN_TIER_2_MS
                        else -> ERROR_COOLDOWN_TIER_3_MS
                    }
                    nextRetryAllowedAt = System.currentTimeMillis() + backoffMs

                    onFailed("${adError.code}: ${adError.message}")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_BOTTOM_LEFT)
                    .build()
            )
            .build()

        //adLoader.loadAd(AdRequest.Builder().build())
        val adRequest = AdRequest.Builder()
            .addKeyword("launcher")
            .addKeyword("app drawer")
            .addKeyword("home screen")
            .addKeyword("android launcher")
            .addKeyword("app organizer")
            .addKeyword("productivity")
            .addKeyword("mobile utility")
            .build()
        
        adLoader.loadAd(adRequest)
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

        val mediaContainer = adView.findViewById<FrameLayout>(R.id.ad_media_container)
        val mediaView = adView.findViewById<MediaView>(R.id.ad_media_view)
        val mediaFallbackText = adView.findViewById<TextView>(R.id.ad_media_fallback_text)
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)

        if (nativeAd.mediaContent != null) {
            // Registering the MediaView is what makes the SDK render the
            // ad's image/video into it. A transparent view sits on top of
            // it in the layout (ad_media_click_blocker) so taps on the
            // media are absorbed there instead of triggering the ad click.
            mediaView.visibility = View.VISIBLE
            mediaFallbackText.visibility = View.GONE
            mediaView.setImageScaleType(ImageView.ScaleType.FIT_CENTER)
            mediaView.mediaContent = nativeAd.mediaContent
            adView.mediaView = mediaView

            // The image/video may not fill the box (different aspect
            // ratio), leaving empty strips around it. Fill those with a
            // color sampled from the media itself so it never looks
            // "empty" — falls back to a neutral tone while nothing has
            // loaded yet or for video content with no still image.
            mediaContainer.setBackgroundColor(
                ContextCompat.getColor(appContext, R.color.sponsored_background)
            )
            val sampleDrawable = nativeAd.images.firstOrNull()?.drawable
            if (sampleDrawable != null) {
                val ambientColor = ambientColorFrom(sampleDrawable)
                if (ambientColor != null) {
                    mediaContainer.setBackgroundColor(ambientColor)
                }
            }
        } else {
            mediaView.visibility = View.GONE
            mediaFallbackText.visibility = View.VISIBLE
            mediaContainer.setBackgroundColor(
                ContextCompat.getColor(appContext, R.color.sponsored_background)
            )
        }

        if (!nativeAd.headline.isNullOrEmpty()) {
            headlineView.text = nativeAd.headline
            headlineView.visibility = View.VISIBLE
        } else {
            headlineView.visibility = View.GONE
        }
        adView.headlineView = headlineView

        if (!nativeAd.callToAction.isNullOrEmpty()) {
            ctaView.text = nativeAd.callToAction
            ctaView.visibility = View.VISIBLE
        } else {
            ctaView.visibility = View.GONE
        }
        adView.callToActionView = ctaView

        adView.setNativeAd(nativeAd)

        container.addView(adView)
        onDisplayChanged()
    }

    /**
     * Downsamples the drawable to a single pixel and reads its color — a
     * cheap way to get an "ambient" average color from an ad image so it
     * can be used as a letterbox fill behind the MediaView, without pulling
     * in a Palette dependency.
     */
    private fun ambientColorFrom(drawable: Drawable): Int? {
        return try {
            val bitmap: Bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }

            val scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
            val pixel = scaled.getPixel(0, 0)
            if (scaled !== bitmap) {
                scaled.recycle()
            }

            // Soften it slightly toward the ad card's white background so
            // it reads as an ambient tint rather than a jarring solid block.
            val r = (Color.red(pixel) * 0.82f + 255 * 0.18f).toInt().coerceIn(0, 255)
            val g = (Color.green(pixel) * 0.82f + 255 * 0.18f).toInt().coerceIn(0, 255)
            val b = (Color.blue(pixel) * 0.82f + 255 * 0.18f).toInt().coerceIn(0, 255)
            Color.rgb(r, g, b)
        } catch (e: Exception) {
            null
        }
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
