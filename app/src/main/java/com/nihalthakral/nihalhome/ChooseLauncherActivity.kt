package com.nihalthakral.nihalhome

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class ChooseLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_launcher)

        val launchers = LauncherUtils.getAvailableLaunchers(this)

        val listContainer = findViewById<LinearLayout>(R.id.launcherListContainer)
        val checkboxDontAskAgain = findViewById<CheckBox>(R.id.checkboxDontAskAgain)
        val buttonLaunchIt = findViewById<Button>(R.id.buttonLaunchIt)

        applyResponsiveButtonTextSize(buttonLaunchIt)

        var selectedLauncher: LauncherAppInfo? = null
        var selectedRow: View? = null

        for (launcherInfo in launchers) {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_launcher, listContainer, false)

            itemView.findViewById<ImageView>(R.id.imageLauncherIcon).setImageDrawable(launcherInfo.icon)
            itemView.findViewById<TextView>(R.id.textLauncherLabel).text = launcherInfo.label
            val imageSelected = itemView.findViewById<ImageView>(R.id.imageLauncherSelected)

            itemView.setOnClickListener {
                selectedRow?.findViewById<ImageView>(R.id.imageLauncherSelected)?.visibility = View.INVISIBLE
                imageSelected.visibility = View.VISIBLE
                selectedRow = itemView
                selectedLauncher = launcherInfo
                buttonLaunchIt.isEnabled = true
                buttonLaunchIt.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_accent))
            }

            listContainer.addView(itemView)
        }

        buttonLaunchIt.setOnClickListener {
            val chosen = selectedLauncher ?: return@setOnClickListener
            val prefs = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)

            if (checkboxDontAskAgain.isChecked) {
                prefs.edit()
                    .putBoolean(PreferenceKeys.KEY_DONT_ASK_AGAIN, true)
                    .putString(PreferenceKeys.KEY_SAVED_LAUNCHER_PACKAGE, chosen.packageName)
                    .putString(PreferenceKeys.KEY_SAVED_LAUNCHER_CLASS, chosen.activityName)
                    .apply()
            } else {
                prefs.edit()
                    .putBoolean(PreferenceKeys.KEY_DONT_ASK_AGAIN, false)
                    .apply()
            }

            LauncherUtils.launchSelected(this, chosen.packageName, chosen.activityName)
            finish()
        }
    }

    /**
     * The button's height/width is fixed in dp (see
     * activity_choose_launcher.xml), so its pixel size already varies per
     * device DPI. Once the button is actually laid out, this measures its
     * real pixel height and derives a text size (a fixed fraction of that
     * height) applied to it - this keeps the label visually proportional
     * to the button on every screen, instead of relying on the platform's
     * default text size which can look inconsistent across DPIs.
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

                // Safety net: if the label would not fit the button's
                // width, shrink the size just enough for it to fit.
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
                // specific label. Words with no descenders don't use the
                // space the font reserves below the baseline, so they
                // visually sit a little above true center. This nudges
                // each label using its own real ink bounds so it lands
                // dead center.
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
     * shape.
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
}
