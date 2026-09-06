package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object LauncherUtils {

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
}
