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

    private fun centerTextVertically(button: Button) {
        val paint = button.paint
        val text = button.text.toString()
        if (text.isEmpty()) return

        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val fm = paint.fontMetrics

        // Where gravity="center" currently places the text's vertical
        // midpoint (based on font metrics), vs. where the text's real ink
        // actually sits (based on measured glyph bounds) - both relative
        // to the baseline.
        val fontMetricCenter = (fm.ascent + fm.descent) / 2f
        val inkCenter = (bounds.top + bounds.bottom) / 2f
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
