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

    /**
     * The three action buttons get their height/width from screen-percentage
     * Guidelines (see activity_language_selection.xml), so their pixel size
     * already varies per device and DPI. Once the buttons are actually laid
     * out, this measures their real pixel height and derives ONE shared text
     * size (a fixed fraction of that height) applied to all three - this
     * keeps every label visually proportional to its button, and identical
     * in size across English / Hindi-Urdu / Next, on every screen. It no
     * longer relies on autoSize picking a size per-button based on each
     * label's own text length, which is what caused English, Hindi/Urdu and
     * Next to end up at different, inconsistent sizes.
     */
    private fun applyResponsiveButtonTextSize(vararg buttons: Button) {
        val root = buttons[0].rootView
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val minHeight = buttons.minOf { it.height }
                if (minHeight <= 0) return // layout not measured yet

                root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // A label ~42% of the button's own height reads as a
                // comfortably filled button without touching its edges.
                var textSizePx = minHeight * 0.42f

                // Safety net: if the longest label ("Hindi/Urdu") would not
                // fit the narrowest button's width, shrink the shared size
                // just enough for it to fit - keeps every button consistent
                // instead of only shrinking that one button's text.
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

                // Gravity="center" centers text using the FONT's ascent/
                // descent metrics, not the actual drawn pixels of the
                // specific label. Words with no descenders ("Next",
                // "English", "Hindi/Urdu") don't use the space the font
                // reserves below the baseline, so they visually sit a
                // little above true center. This nudges each label using
                // its own real ink bounds so it lands dead center.
                buttons.forEach { button -> centerTextVertically(button) }
            }
        })
    }

    /**
     * Renders the button's own label onto a small offscreen bitmap (with
     * the exact same paint - size, typeface, everything) and scans it to
     * find the topmost and bottommost row that actually has ink. This is
     * pixel-exact: it reflects precisely what will be drawn on screen, so
     * it centers correctly for ANY text, regardless of whether the font's
     * reported ascent/descent metrics happen to match that word's real
     * shape (which is what caused "Next" to stay off-center previously,
     * even though it fixed "English" and the Hindi/Urdu label).
     */
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
        val baselineY = -top.toFloat() // baseline sits 'top' px below the bitmap's top edge
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
        if (inkTop == -1) return // nothing drawn (blank text) - nothing to center

        // Real ink's vertical midpoint, relative to the baseline.
        val inkCenter = (inkTop + inkBottom) / 2f + top

        // Where gravity="center" currently anchors the line, relative to
        // the baseline, using font metrics (since includeFontPadding is
        // "false").
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
