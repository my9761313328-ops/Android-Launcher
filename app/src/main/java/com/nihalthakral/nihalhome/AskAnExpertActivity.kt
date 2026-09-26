package com.nihalthakral.nihalhome

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class AskAnExpertActivity : ComponentActivity() {

    private val videoUrls = listOf(
        "https://backend-nihalhome.github.io/1.mp4",
        "https://backend-nihalhome.github.io/2.mp4",
        "https://backend-nihalhome.github.io/3.mp4",
        "https://backend-nihalhome.github.io/4.mp4"
    )

    private val shareAppLink = "https://example.com/"

    private var currentIndex = 0

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var imageNoInternet: ImageView
    private lateinit var buttonPrevious: Button
    private lateinit var buttonNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ask_an_expert)

        webView = findViewById(R.id.webViewHowToUse)
        progressBar = findViewById(R.id.progressHowToUse)
        imageNoInternet = findViewById(R.id.imageNoInternet)

        buttonPrevious = findViewById(R.id.buttonPrevious)
        buttonNext = findViewById(R.id.buttonNext)
        val buttonShareApp = findViewById<Button>(R.id.buttonShareApp)
        val emojiShareApp = findViewById<android.widget.TextView>(R.id.emojiShareApp)

        applyHindiStaticText(buttonPrevious, buttonNext, buttonShareApp)

        webView.settings.javaScriptEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = android.view.View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showNoInternet()
                }
            }
        }

        playCurrentVideo()

        applyResponsiveButtonTextSize(emojiShareApp, buttonPrevious, buttonNext, buttonShareApp)

        buttonPrevious.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex -= 1
                playCurrentVideo()
            }
        }

        buttonNext.setOnClickListener {
            if (currentIndex < videoUrls.size - 1) {
                currentIndex += 1
                playCurrentVideo()
            }
        }

        buttonShareApp.setOnClickListener {
            shareApp()
        }

        startShareButtonHeartbeat(findViewById(R.id.shareAppContainer))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToMainScreen()
            }
        })
    }

    private fun applyHindiStaticText(buttonPrevious: Button, buttonNext: Button, buttonShareApp: Button) {
        if (!LocalizationHelper.isHindiSelected(this)) return

        buttonPrevious.text = getString(R.string.action_previous_hi)
        buttonNext.text = getString(R.string.action_next_hi)
        buttonShareApp.text = getString(R.string.action_share_app_hi)
    }

    private fun playCurrentVideo() {
        updateNavButtonsState()

        if (!isNetworkAvailable()) {
            showNoInternet()
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        webView.visibility = android.view.View.VISIBLE
        imageNoInternet.visibility = android.view.View.GONE
        webView.loadUrl(videoUrls[currentIndex])
    }

    private fun updateNavButtonsState() {
        val isFirst = currentIndex == 0
        val isLast = currentIndex == videoUrls.size - 1

        buttonPrevious.isEnabled = !isFirst
        buttonPrevious.alpha = if (isFirst) 0.4f else 1f

        buttonNext.isEnabled = !isLast
        buttonNext.alpha = if (isLast) 0.4f else 1f
    }

    private fun showNoInternet() {
        progressBar.visibility = android.view.View.GONE
        webView.visibility = android.view.View.GONE
        imageNoInternet.visibility = android.view.View.VISIBLE
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, shareAppLink)
        startActivity(Intent.createChooser(intent, getString(R.string.action_share_app)))
    }

    private fun goToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()

        if (imageNoInternet.visibility == android.view.View.VISIBLE && isNetworkAvailable()) {
            playCurrentVideo()
        }
    }

    private fun startShareButtonHeartbeat(target: View) {
        val scaleXKeyframes = PropertyValuesHolder.ofKeyframe(
            View.SCALE_X,
            Keyframe.ofFloat(0f, 1f),
            Keyframe.ofFloat(0.06f, 1.06f),
            Keyframe.ofFloat(0.14f, 1f),
            Keyframe.ofFloat(0.20f, 1.05f),
            Keyframe.ofFloat(0.28f, 1f),
            Keyframe.ofFloat(1f, 1f)
        )

        val scaleYKeyframes = PropertyValuesHolder.ofKeyframe(
            View.SCALE_Y,
            Keyframe.ofFloat(0f, 1f),
            Keyframe.ofFloat(0.06f, 1.06f),
            Keyframe.ofFloat(0.14f, 1f),
            Keyframe.ofFloat(0.20f, 1.05f),
            Keyframe.ofFloat(0.28f, 1f),
            Keyframe.ofFloat(1f, 1f)
        )

        val heartbeat = ObjectAnimator.ofPropertyValuesHolder(target, scaleXKeyframes, scaleYKeyframes)
        heartbeat.duration = 2600L
        heartbeat.repeatCount = ObjectAnimator.INFINITE
        heartbeat.start()
    }

    private fun applyResponsiveButtonTextSize(emojiView: android.widget.TextView, vararg buttons: Button) {
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
                emojiView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)

                buttons.forEach { button -> centerTextVertically(button) }
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
}
