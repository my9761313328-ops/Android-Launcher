package com.nihalthakral.nihalhome

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

data class InstalledAppsResult(
    val apps: List<InstalledApp>
)

object InstalledAppsScanner {

    fun performScan(context: Context): InstalledAppsResult {
        val packageManager = context.packageManager

        val installedApps: List<ApplicationInfo> = try {
            packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
            )
        } catch (e: Exception) {
            try {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (e2: Exception) {
                emptyList()
            }
        }

        val apps = installedApps
            .asSequence()
            .filterNot { isSystemApp(it) }
            .filterNot { it.packageName == context.packageName }
            .distinctBy { it.packageName }
            .map { appInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = safeLabel(packageManager, appInfo),
                    icon = safeIcon(packageManager, appInfo)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

        return InstalledAppsResult(apps = apps)
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
