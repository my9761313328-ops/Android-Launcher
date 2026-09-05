package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
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

        private const val ERROR_COOLDOWN_TIER_1_MS = 15L * 60L * 1000L  
        private const val ERROR_COOLDOWN_TIER_2_MS = 30L * 60L * 1000L  
        private const val ERROR_COOLDOWN_TIER_3_MS = 60L * 60L * 1000L  

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
            
            cachedNativeAd = null
            hideShimmer()
            showOnScreen(cached)
            lastAdShownTime = now
            return
        }

        showGameFallbackCard(AD_FALLBACK_TAG)
    }

    private fun fitMediaToAspectRatio(container: View, mediaView: View, aspectRatio: Float) {
        if (aspectRatio <= 0f) return
        container.post {
            val width = container.width
            if (width > 0) {
                val exactHeight = (width / aspectRatio).toInt().coerceAtLeast(1)
                val params = mediaView.layoutParams
                if (params.height != exactHeight) {
                    params.height = exactHeight
                    mediaView.layoutParams = params
                }
            }
        }
    }

    private fun showGameFallbackCard(tag: String) {
        if (isDestroyed) return
        displayedNativeAd?.destroy()
        displayedNativeAd = null
        
        shimmerView?.stopShimmer()
        shimmerView = null
        container.removeAllViews()

        val view = LayoutInflater.from(appContext)
            .inflate(R.layout.offline_game_layout, container, false)
        view.tag = tag

        val mediaContainer = view.findViewById<FrameLayout>(R.id.offlineGameMediaContainer)
        val mediaView = view.findViewById<ImageView>(R.id.offlineGameMedia)
        val drawable = mediaView.drawable
        val aspectRatio = if (drawable != null && drawable.intrinsicHeight > 0) {
            drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
        } else {
            0f
        }
        fitMediaToAspectRatio(mediaContainer, mediaView, aspectRatio)

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
                
                consecutiveAdErrorCount = 0
                nextRetryAllowedAt = 0L
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isFetchInFlight = false
                    Log.e(TAG, "Native ad failed to load: ${adError.message}")

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
        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)
        val starRatingView = adView.findViewById<RatingBar>(R.id.ad_star_rating)
        val priceView = adView.findViewById<TextView>(R.id.ad_price)
        val storeView = adView.findViewById<TextView>(R.id.ad_store)
        val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
        val contentRow = adView.findViewById<LinearLayout>(R.id.ad_content_row)

        if (nativeAd.mediaContent != null) {
            
            mediaView.visibility = View.VISIBLE
            mediaFallbackText.visibility = View.GONE
            mediaView.setImageScaleType(ImageView.ScaleType.FIT_CENTER)
            mediaView.mediaContent = nativeAd.mediaContent
            adView.mediaView = mediaView

            fitMediaToAspectRatio(mediaContainer, mediaView, nativeAd.mediaContent?.aspectRatio ?: 0f)

            mediaContainer.setBackgroundColor(
                ContextCompat.getColor(appContext, R.color.sponsored_background)
            )
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

        val icon = nativeAd.icon
        if (icon?.drawable != null) {
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }
        adView.iconView = iconView

        if (!nativeAd.body.isNullOrEmpty()) {
            bodyView.text = nativeAd.body
            bodyView.visibility = View.VISIBLE
        } else {
            bodyView.visibility = View.GONE
        }
        adView.bodyView = bodyView

        if (!nativeAd.advertiser.isNullOrEmpty()) {
            advertiserView.text = nativeAd.advertiser
            advertiserView.visibility = View.VISIBLE
        } else {
            advertiserView.visibility = View.GONE
        }
        adView.advertiserView = advertiserView

        val starRating = nativeAd.starRating
        if (starRating != null) {
            starRatingView.rating = starRating.toFloat()
            starRatingView.visibility = View.VISIBLE
        } else {
            starRatingView.visibility = View.GONE
        }
        adView.starRatingView = starRatingView

        if (!nativeAd.price.isNullOrEmpty()) {
            priceView.text = nativeAd.price
            priceView.visibility = View.VISIBLE
        } else {
            priceView.visibility = View.GONE
        }
        adView.priceView = priceView

        if (!nativeAd.store.isNullOrEmpty()) {
            storeView.text = nativeAd.store
            storeView.visibility = View.VISIBLE
        } else {
            storeView.visibility = View.GONE
        }
        adView.storeView = storeView

        if (!nativeAd.callToAction.isNullOrEmpty()) {
            ctaView.text = nativeAd.callToAction
            ctaView.visibility = View.VISIBLE
        } else {
            ctaView.visibility = View.GONE
        }
        adView.callToActionView = ctaView

        adView.setNativeAd(nativeAd)

        // AdMob SDK setNativeAd() ke andar internally in registered views (headline, icon,
        // body, media, advertiser, rating, price, store) par apna khud ka click/touch
        // listener attach kar deta hai — sirf isClickable = false karne se ye override nahi
        // hota. Isliye har non-CTA view par touch event ko consume karke block karte hain
        // taaki touch signal parent AdView tak na pahunche aur ad-click trigger na ho.
        val blockTouchListener = View.OnTouchListener { _, _ -> true }

        headlineView.setOnTouchListener(blockTouchListener)
        iconView.setOnTouchListener(blockTouchListener)
        bodyView.setOnTouchListener(blockTouchListener)
        mediaView.setOnTouchListener(blockTouchListener)
        advertiserView.setOnTouchListener(blockTouchListener)
        starRatingView.setOnTouchListener(blockTouchListener)
        priceView.setOnTouchListener(blockTouchListener)
        storeView.setOnTouchListener(blockTouchListener)
        contentRow.setOnTouchListener(blockTouchListener)

        // Sirf CTA button normal/active rahe taaki ad click sirf yahin se trigger ho
        ctaView.setOnTouchListener(null)
        ctaView.isClickable = true
        ctaView.isFocusable = true

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
