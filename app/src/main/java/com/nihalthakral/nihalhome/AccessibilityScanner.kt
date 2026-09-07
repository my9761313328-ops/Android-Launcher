package com.nihalthakral.nihalhome

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.accessibility.AccessibilityManager

/**
 * A single app that will be printed to the terminal-style log while scanning.
 */
data class ScannedAppEntry(
    val packageName: String,
    val label: String
)

/**
 * An app that declares an Accessibility Service, is not a system app/service,
 * and is therefore flagged as High Risk regardless of whether the service is
 * currently enabled or disabled by the user.
 */
data class FlaggedAccessibilityApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

data class ScanResult(
    val scannedApps: List<ScannedAppEntry>,
    val flaggedApps: List<FlaggedAccessibilityApp>
)

object AccessibilityScanner {

    /**
     * Performs the full scan. Safe to call from a background thread only —
     * it touches PackageManager for every installed app on the device.
     */
    fun performScan(context: Context): ScanResult {
        val packageManager = context.packageManager

        // 1. Every installed app on the device, no filtering — used purely
        // for the terminal-style "scanning..." feed.
        val installedApps: List<ApplicationInfo> = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        val scannedApps = installedApps
            .map { appInfo ->
                ScannedAppEntry(
                    packageName = appInfo.packageName,
                    label = safeLabel(packageManager, appInfo)
                )
            }
            .sortedBy { it.packageName }

        // 2. All apps/services that declare an Accessibility Service,
        // whether the user currently has it enabled or disabled.
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

        val declaredServices = try {
            accessibilityManager?.installedAccessibilityServiceList.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

        val flaggedApps = declaredServices
            .mapNotNull { serviceInfo -> serviceInfo.resolveInfo?.serviceInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filterNot { isSystemApp(it) }
            .map { appInfo ->
                FlaggedAccessibilityApp(
                    packageName = appInfo.packageName,
                    label = safeLabel(packageManager, appInfo),
                    icon = safeIcon(packageManager, appInfo)
                )
            }
            .sortedBy { it.label.lowercase() }

        return ScanResult(scannedApps = scannedApps, flaggedApps = flaggedApps)
    }

    /**
     * A system app or a system-updated app/service is never flagged —
     * only third-party apps with Accessibility access are a concern.
     */
    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        val isBuiltInSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return isBuiltInSystemApp || isUpdatedSystemApp
    }

    private fun safeLabel(packageManager: PackageManager, appInfo: ApplicationInfo): String {
        return try {
            appInfo.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            appInfo.packageName
        }
    }

    private fun safeIcon(packageManager: PackageManager, appInfo: ApplicationInfo): Drawable {
        return try {
            appInfo.loadIcon(packageManager)
        } catch (e: Exception) {
            packageManager.defaultActivityIcon
        }
    }
}
