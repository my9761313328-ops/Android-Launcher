package com.nihalthakral.nihalhome

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

class ScalableDrawable(
    private val wrapped: Drawable,
    private val targetSize: Int
) : Drawable() {

    override fun getIntrinsicWidth(): Int = targetSize

    override fun getIntrinsicHeight(): Int = targetSize

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        wrapped.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        wrapped.bounds = bounds
        wrapped.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        wrapped.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        wrapped.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun isStateful(): Boolean = wrapped.isStateful

    override fun setState(stateSet: IntArray): Boolean {
        wrapped.state = stateSet
        return super.setState(stateSet)
    }

    override fun jumpToCurrentState() {
        wrapped.jumpToCurrentState()
    }
}
