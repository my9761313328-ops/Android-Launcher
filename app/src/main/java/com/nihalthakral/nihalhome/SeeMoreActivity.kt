package com.nihalthakral.nihalhome

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class SeeMoreActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_see_more)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToMainScreen()
            }
        })

        fitComingSoonText()
    }

    private fun fitComingSoonText() {
        val root = findViewById<FrameLayout>(R.id.containerSeeMoreRoot)
        val textView = findViewById<TextView>(R.id.textSeeMoreComingSoon)

        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val rootWidth = root.width
                val rootHeight = root.height
                if (rootWidth <= 0 || rootHeight <= 0) return

                root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val availableWidth = rootWidth * HORIZONTAL_CONTENT_FRACTION
                val availableHeight = rootHeight * VERTICAL_CONTENT_FRACTION

                val paint = textView.paint
                val text = textView.text.toString()

                var textSizePx = availableHeight
                paint.textSize = textSizePx

                while (textSizePx > 1f) {
                    paint.textSize = textSizePx
                    val fm = paint.fontMetrics
                    val lineHeight = fm.bottom - fm.top
                    val textWidth = paint.measureText(text)
                    if (textWidth <= availableWidth && lineHeight <= availableHeight) break
                    textSizePx -= 1f
                }

                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            }
        })
    }

    private fun goToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val HORIZONTAL_CONTENT_FRACTION = 0.8f
        private const val VERTICAL_CONTENT_FRACTION = 0.8f
    }
}
