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
import androidx.core.widget.CompoundButtonCompat

class ChooseLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_launcher)

        val launchers = LauncherUtils.getAvailableLaunchers(this)

        val listContainer = findViewById<LinearLayout>(R.id.launcherListContainer)
        val checkboxDontAskAgain = findViewById<CheckBox>(R.id.checkboxDontAskAgain)
        val buttonSubmit = findViewById<Button>(R.id.buttonSubmit)

        applyResponsiveButtonTextSize(buttonSubmit)
        applyResponsiveCheckboxIcon(checkboxDontAskAgain)

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
                buttonSubmit.isEnabled = true
                buttonSubmit.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_accent))
            }

            listContainer.addView(itemView)
        }

        buttonSubmit.setOnClickListener {
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

    private fun applyResponsiveCheckboxIcon(checkBox: CheckBox) {
        val originalDrawable = CompoundButtonCompat.getButtonDrawable(checkBox) ?: return

        checkBox.minWidth = 0
        checkBox.minHeight = 0
        checkBox.minimumWidth = 0
        checkBox.minimumHeight = 0

        val maxSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 500f, checkBox.resources.displayMetrics
        ).toInt()

        checkBox.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val availableHeight = checkBox.height - checkBox.paddingTop - checkBox.paddingBottom
                if (availableHeight <= 0) return

                checkBox.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val targetSize = availableHeight.coerceAtMost(maxSizePx)
                checkBox.buttonDrawable = ScalableDrawable(originalDrawable, targetSize)

                val density = checkBox.resources.displayMetrics.density
                val paddingPx = (8 * density).toInt()
                checkBox.compoundDrawablePadding = paddingPx

                val availableTextWidth =
                    (checkBox.width - checkBox.paddingLeft - checkBox.paddingRight - targetSize - paddingPx).toFloat()
                if (availableTextWidth <= 0f) return

                val paint = checkBox.paint
                var textSizePx = availableHeight * 0.42f
                paint.textSize = textSizePx
                while (paint.measureText(checkBox.text.toString()) > availableTextWidth && textSizePx > 1f) {
                    textSizePx -= 1f
                    paint.textSize = textSizePx
                }
                checkBox.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            }
        })
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
