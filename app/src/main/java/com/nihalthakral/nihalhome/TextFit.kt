package com.nihalthakral.nihalhome

import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.ViewTreeObserver
import android.widget.TextView

/**
 * Fits this TextView's text size exactly to its own final measured height
 * (in pixels), regardless of ConstraintLayout guideline % used to size it.
 *
 * Works around the known unreliability of android:autoSizeTextType inside
 * ConstraintLayout when the view's height is derived from percentage
 * guidelines (0dp / match_constraint). Instead of relying on Android's
 * autosize pass, we read the view's actual laid-out pixel height once
 * layout is complete, then binary-search the largest text size whose
 * rendered glyph bounds fit inside that height. The result is applied in
 * pixels via setTextSize(TypedValue.COMPLEX_UNIT_PX, ...), so it is not
 * affected by the user's system font-size setting scaling it back out of
 * the box.
 *
 * Call this once after setContentView(), e.g.:
 *   textChooseLanguage.fitTextToViewHeight()
 */
fun TextView.fitTextToViewHeight(minPx: Float = 1f, maxPx: Float = 500f) {
    // Wait for a real layout pass so height/width reflect the resolved
    // ConstraintLayout guidelines, not 0 (pre-layout).
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)

            val availableHeight = height - paddingTop - paddingBottom
            val availableWidth = width - paddingLeft - paddingRight
            val content = text?.toString()?.takeIf { it.isNotEmpty() } ?: return
            if (availableHeight <= 0 || availableWidth <= 0) return

            val testPaint = Paint(paint)
            val bounds = Rect()
            var lo = minPx
            var hi = maxPx
            var best = lo

            // Binary search the largest text size (in px) whose rendered
            // glyph bounds fit within BOTH the exact height and the width.
            repeat(30) {
                val mid = (lo + hi) / 2f
                testPaint.textSize = mid
                testPaint.getTextBounds(content, 0, content.length, bounds)
                val fitsHeight = bounds.height() <= availableHeight
                val fitsWidth = bounds.width() <= availableWidth
                if (fitsHeight && fitsWidth) {
                    best = mid
                    lo = mid
                } else {
                    hi = mid
                }
            }

            setTextSize(TypedValue.COMPLEX_UNIT_PX, best)
        }
    })
}
