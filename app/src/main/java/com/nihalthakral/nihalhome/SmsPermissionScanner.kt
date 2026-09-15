package com.nihalthakral.nihalhome

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Telephony

data class SmsPermissionApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isDefaultSmsHandler: Boolean
)

object SmsPermissionScanner {

    private val smsPermissions = listOf(
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.WRITE_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.RECEIVE_WAP_PUSH"
    )

    fun scan(context: Context): List<SmsPermissionApp> {
        val packageManager = context.packageManager
        val ownPackageName = context.packageName
        val defaultSmsPackage = try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (e: Exception) {
            null
        }

        val installedApps: List<ApplicationInfo> = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        return installedApps
            .filterNot { it.packageName == ownPackageName }
            .filterNot { isSystemApp(it) }
            .filter { hasSmsPermissionGranted(packageManager, it.packageName) }
            .map { appInfo ->
                SmsPermissionApp(
                    packageName = appInfo.packageName,
                    label = safeLabel(packageManager, appInfo),
                    icon = safeIcon(packageManager, appInfo),
                    isDefaultSmsHandler = appInfo.packageName == defaultSmsPackage
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun hasSmsPermissionGranted(packageManager: PackageManager, packageName: String): Boolean {
        return smsPermissions.any { permission ->
            try {
                packageManager.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }
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
