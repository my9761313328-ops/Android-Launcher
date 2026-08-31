package com.nihalthakral.nihalhome

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
    // True when this app is a pre-installed system/OEM app (ApplicationInfo.FLAG_SYSTEM).
    // Used to prefer the real OEM Phone/Messages/Camera app over a user-installed
    // replacement (e.g. Truecaller) when both are candidates for the same core-app slot.
    val isSystemApp: Boolean = false
)
