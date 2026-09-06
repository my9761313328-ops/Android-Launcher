package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class LauncherAppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
)

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

    fun getAvailableLaunchers(context: Context): List<LauncherAppInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)

        val queryFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS
        } else {
            PackageManager.MATCH_DEFAULT_ONLY
        }

        val resolveInfos = try {
            packageManager.queryIntentActivities(intent, queryFlags)
        } catch (e: Exception) {
            packageManager.queryIntentActivities(intent, 0)
        }

        val seen = HashSet<String>()
        val launchers = mutableListOf<LauncherAppInfo>()

        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val key = activityInfo.packageName + "/" + activityInfo.name
            if (!seen.add(key)) continue

            val label = try {
                resolveInfo.loadLabel(packageManager)?.toString()
            } catch (e: Exception) {
                null
            } ?: activityInfo.packageName

            val icon = try {
                resolveInfo.loadIcon(packageManager)
            } catch (e: Exception) {
                null
            } ?: packageManager.defaultActivityIcon

            launchers.add(
                LauncherAppInfo(
                    label = label,
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    icon = icon
                )
            )
        }

        return launchers.sortedBy { it.label.lowercase() }
    }

    fun launchSelected(context: Context, packageName: String, activityName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                setClassName(packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
