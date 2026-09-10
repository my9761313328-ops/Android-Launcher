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
        val allLaunchers = mutableListOf<LauncherAppInfo>()
        val realLaunchers = mutableListOf<LauncherAppInfo>()

        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val key = activityInfo.packageName + "/" + activityInfo.name
            if (!seen.add(key)) continue

            val rawLabel = try {
                resolveInfo.loadLabel(packageManager)?.toString()
            } catch (e: Exception) {
                null
            }
            val label = rawLabel ?: activityInfo.packageName

            val icon = try {
                resolveInfo.loadIcon(packageManager)
            } catch (e: Exception) {
                null
            } ?: packageManager.defaultActivityIcon

            val info = LauncherAppInfo(
                label = label,
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                icon = icon
            )

            // Unfiltered list kept as a fallback, so we never risk showing
            // zero launchers on some unusual device/OEM.
            allLaunchers.add(info)

            // "Real" launcher = actually enabled on this device (not a
            // disabled/hidden system stub) AND has a genuine label (not
            // just its raw package name, which happens for nameless stub
            // components) AND isn't our own app.
            val isEnabled = isComponentEnabled(packageManager, activityInfo)
            val hasRealLabel = rawLabel != null && rawLabel.isNotBlank()
            val isOwnApp = activityInfo.packageName == context.packageName

            if (isEnabled && hasRealLabel && !isOwnApp) {
                realLaunchers.add(info)
            }
        }

        // Zero-risk fallback: if the filtered list is empty for any reason
        // (unusual device/OEM behavior), fall back to showing everything,
        // exactly like before this filtering existed.
        val result = if (realLaunchers.isNotEmpty()) realLaunchers else allLaunchers

        return result.sortedBy { it.label.lowercase() }
    }

    private fun isComponentEnabled(
        packageManager: PackageManager,
        activityInfo: android.content.pm.ActivityInfo
    ): Boolean {
        return try {
            val componentName = android.content.ComponentName(activityInfo.packageName, activityInfo.name)
            val setting = packageManager.getComponentEnabledSetting(componentName)
            when (setting) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> activityInfo.isEnabled && activityInfo.applicationInfo.enabled
            }
        } catch (e: Exception) {
            activityInfo.isEnabled
        }
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
