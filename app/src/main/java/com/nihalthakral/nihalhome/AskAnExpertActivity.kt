package com.nihalthakral.nihalhome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.VideoView
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

    private lateinit var videoView: VideoView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ask_an_expert)

        videoView = findViewById(R.id.videoHowToUse)
        progressBar = findViewById(R.id.progressHowToUse)

        val buttonPrevious = findViewById<Button>(R.id.buttonPrevious)
        val buttonNext = findViewById<Button>(R.id.buttonNext)
        val buttonShareApp = findViewById<Button>(R.id.buttonShareApp)

        applyHindiStaticText(buttonPrevious, buttonNext, buttonShareApp)

        videoView.setOnPreparedListener { player ->
            progressBar.visibility = android.view.View.GONE
            player.isLooping = true
            player.start()
        }

        videoView.setOnErrorListener { _, _, _ ->
            progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, getString(R.string.error_video_playback), Toast.LENGTH_SHORT).show()
            true
        }

        playCurrentVideo()

        applyResponsiveButtonTextSize(buttonPrevious, buttonNext, buttonShareApp)

        buttonPrevious.setOnClickListener {
            currentIndex = if (currentIndex == 0) videoUrls.size - 1 else currentIndex - 1
            playCurrentVideo()
        }

        buttonNext.setOnClickListener {
            currentIndex = if (currentIndex == videoUrls.size - 1) 0 else currentIndex + 1
            playCurrentVideo()
        }

        buttonShareApp.setOnClickListener {
            shareApp()
        }

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
        progressBar.visibility = android.view.View.VISIBLE
        videoView.setVideoURI(Uri.parse(videoUrls[currentIndex]))
        videoView.requestFocus()
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
        if (videoView.isPlaying) {
            videoView.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!videoView.isPlaying) {
            videoView.start()
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
