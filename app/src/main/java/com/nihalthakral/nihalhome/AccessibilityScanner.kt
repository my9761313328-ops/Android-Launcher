package com.nihalthakral.nihalhome

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

/**
 * A single app that will be printed to the terminal-style log while scanning.
 */
data class ScannedAppEntry(
    val packageName: String,
    val label: String
)

/**
 * An app that has an Accessibility Service currently ENABLED by the user,
 * and is not a system app/service — flagged so the user can review and
 * turn it off if it's not something they trust.
 */
data class FlaggedAccessibilityApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val serviceClassName: String
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

        // 2. Only apps/services whose Accessibility Service is CURRENTLY
        // ENABLED by the user. Services that are merely installed/declared
        // but switched off are not flagged.
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

        val enabledServices = try {
            accessibilityManager
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

        val flaggedApps = enabledServices
            .mapNotNull { serviceInfo ->
                val resolvedServiceInfo = serviceInfo.resolveInfo?.serviceInfo ?: return@mapNotNull null
                val appInfo = resolvedServiceInfo.applicationInfo ?: return@mapNotNull null
                Pair(appInfo, resolvedServiceInfo.name)
            }
            .distinctBy { it.first.packageName }
            .filterNot { isSystemApp(it.first) }
            .map { (appInfo, serviceClassName) ->
                FlaggedAccessibilityApp(
                    packageName = appInfo.packageName,
                    label = safeLabel(packageManager, appInfo),
                    icon = safeIcon(packageManager, appInfo),
                    serviceClassName = serviceClassName
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
