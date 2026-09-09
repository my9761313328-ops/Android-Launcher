package com.nihalthakral.nihalhome

import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.widget.TextView

/**
 * Fits this TextView's text size exactly to its own final measured height
 * (in pixels), regardless of ConstraintLayout guideline % used to size it.
 *
 * IMPORTANT: this must be scheduled with View.post{}, not with
 * viewTreeObserver.addOnGlobalLayoutListener called immediately after
 * setContentView(). Registering a global-layout listener that early
 * attaches to a temporary ViewTreeObserver that is discarded once the
 * view hierarchy actually attaches to the window - so the listener
 * silently never fires and the text stays at its XML default size.
 * View.post() is specifically designed to defer until the view is
 * attached and has completed its layout pass, so it is used here instead.
 *
 * Call this once after setContentView(), e.g.:
 *   textChooseLanguage.fitTextToViewHeight()
 */
fun TextView.fitTextToViewHeight(minPx: Float = 1f, maxPx: Float = 500f, attemptsLeft: Int = 5) {
    post {
        val availableHeight = height - paddingTop - paddingBottom
        val availableWidth = width - paddingLeft - paddingRight

        // Layout may not have fully resolved the guideline-based size yet
        // on the very first pass; retry a few times via post() until it has.
        if ((availableHeight <= 0 || availableWidth <= 0) && attemptsLeft > 0) {
            fitTextToViewHeight(minPx, maxPx, attemptsLeft - 1)
            return@post
        }
        if (availableHeight <= 0 || availableWidth <= 0) return@post

        val content = text?.toString()?.takeIf { it.isNotEmpty() } ?: return@post
        val testPaint = Paint(paint)
        var lo = minPx
        var hi = maxPx
        var best = lo

        // Binary search the largest text size (in px) whose actual
        // rendered LINE height (ascent-to-descent, what Android really
        // reserves/draws for a line - not just the glyph ink bounds)
        // fits within the exact available height, and whose measured
        // text width fits the available width.
        repeat(30) {
            val mid = (lo + hi) / 2f
            testPaint.textSize = mid
            val fm = testPaint.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            val textWidth = testPaint.measureText(content)
            if (lineHeight <= availableHeight && textWidth <= availableWidth) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }

        setTextSize(TypedValue.COMPLEX_UNIT_PX, best)

        // Hard safety net: even if some device/font renders a hair
        // outside this box, physically clip drawing to the view's own
        // bounds so text can never bleed into neighbouring space.
        clipBounds = Rect(0, 0, width, height)
    }
}

