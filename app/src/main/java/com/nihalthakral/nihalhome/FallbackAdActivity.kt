package com.nihalthakral.nihalhome

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class FallbackAdActivity : ComponentActivity() {

    private lateinit var videoView: VideoView
    private var resultHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fallback_ad)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        videoView = findViewById(R.id.fallbackAdVideo)
        videoView.setOnPreparedListener { player ->
            player.isLooping = false
            videoView.start()
        }
        videoView.setOnCompletionListener {
            finishWithResult(true)
        }
        videoView.setOnErrorListener { _, _, _ ->
            finishWithResult(false)
            true
        }
        videoView.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.example_ad}"))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        finishWithResult(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::videoView.isInitialized) videoView.stopPlayback()
    }

    private fun finishWithResult(completed: Boolean) {
        if (resultHandled) return
        resultHandled = true
        setResult(if (completed) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
