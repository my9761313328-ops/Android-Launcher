package com.nihalthakral.nihalhome

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

data class ScannedAppEntry(
    val packageName: String,
    val label: String
)

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

    fun performScan(context: Context): ScanResult {
        val packageManager = context.packageManager

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
