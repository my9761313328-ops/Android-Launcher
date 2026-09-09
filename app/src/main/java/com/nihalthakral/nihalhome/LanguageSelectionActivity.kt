package com.nihalthakral.nihalhome

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewTreeObserver
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class LanguageSelectionActivity : ComponentActivity() {

    private var selectedLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_selection)

        val buttonEnglish = findViewById<Button>(R.id.buttonEnglish)
        val buttonHindiUrdu = findViewById<Button>(R.id.buttonHindiUrdu)
        val buttonNext = findViewById<Button>(R.id.buttonNext)

        applyResponsiveButtonTextSize(buttonEnglish, buttonHindiUrdu, buttonNext)

        buttonEnglish.setOnClickListener {
            selectedLanguage = PreferenceKeys.LANGUAGE_ENGLISH
            updateSelectionState(buttonEnglish, buttonHindiUrdu, buttonNext)
        }

        buttonHindiUrdu.setOnClickListener {
            selectedLanguage = PreferenceKeys.LANGUAGE_HINDI_URDU
            updateSelectionState(buttonHindiUrdu, buttonEnglish, buttonNext)
        }

        buttonNext.setOnClickListener {
            val language = selectedLanguage
            if (language != null) {
                getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PreferenceKeys.KEY_SELECTED_LANGUAGE, language)
                    .apply()
                startActivity(Intent(this, DefaultLauncherActivity::class.java))
                finish()
            }
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

    private fun updateSelectionState(selected: Button, unselected: Button, next: Button) {
        selected.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_accent))
        selected.setTextColor(ContextCompat.getColor(this, R.color.onboarding_button_text))
        unselected.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_button_unselected))
        unselected.setTextColor(ContextCompat.getColor(this, R.color.onboarding_heading))
        next.isEnabled = true
        next.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_accent))
        next.setTextColor(ContextCompat.getColor(this, R.color.onboarding_button_text))
    }
}
