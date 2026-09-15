package com.nihalthakral.nihalhome

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class FlaggedSmsApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

data class SmsScanResult(
    val flaggedApps: List<FlaggedSmsApp>
)

object SmsPermissionScanner {

    private val TARGET_PERMISSIONS = setOf(
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.RECEIVE_SMS
    )

    fun performScan(context: Context): SmsScanResult {
        val packageManager = context.packageManager

        val installedApps: List<ApplicationInfo> = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        val flaggedApps = installedApps
            .filterNot { isSystemApp(it) }
            .filter { hasGrantedSmsPermission(packageManager, it.packageName) }
            .map { appInfo ->
                FlaggedSmsApp(
                    packageName = appInfo.packageName,
                    label = safeLabel(packageManager, appInfo),
                    icon = safeIcon(packageManager, appInfo)
                )
            }
            .sortedBy { it.label.lowercase() }

        return SmsScanResult(flaggedApps = flaggedApps)
    }

    private fun hasGrantedSmsPermission(packageManager: PackageManager, packageName: String): Boolean {
        val packageInfo: PackageInfo = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            return false
        }

        val requestedPermissions = packageInfo.requestedPermissions ?: return false
        val requestedFlags = packageInfo.requestedPermissionsFlags ?: return false

        for (i in requestedPermissions.indices) {
            val permission = requestedPermissions[i]
            if (permission !in TARGET_PERMISSIONS) continue
            val isGranted = (requestedFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            if (isGranted) return true
        }

        return false
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
