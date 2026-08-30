package com.nihalthakral.nihalhome

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import kotlin.math.min
import kotlin.math.roundToInt

object ScreenAdapter {

    private const val REFERENCE_SMALLEST_WIDTH_DP = 399f

    fun wrap(base: Context): Context {
        val deviceMetrics = base.applicationContext.resources.displayMetrics
        val smallestWidthPx = min(deviceMetrics.widthPixels, deviceMetrics.heightPixels)

        val targetDensity = smallestWidthPx / REFERENCE_SMALLEST_WIDTH_DP
        val targetDensityDpi = (targetDensity * DisplayMetrics.DENSITY_DEFAULT).roundToInt()

        val configuration = Configuration(base.resources.configuration)
        configuration.densityDpi = targetDensityDpi

        return base.createConfigurationContext(configuration)
    }
}
