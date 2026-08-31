package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore

object AppRepository {

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val selfPackage = context.packageName

        return resolveInfos
            .asSequence()
            .filter { it.activityInfo.packageName != selfPackage }
            .map { ri ->
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    activityName = ri.activityInfo.name,
                    icon = ri.loadIcon(pm),
                    isSystemApp = isSystemPackage(pm, ri.activityInfo.packageName)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun loadAllInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val selfPackage = context.packageName

        val installedApps = pm.getInstalledApplications(0)

        return installedApps
            .asSequence()
            .filter { it.packageName != selfPackage }
            .mapNotNull { appInfo ->
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                val component = launchIntent?.component ?: return@mapNotNull null

                AppInfo(
                    label = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    activityName = component.className,
                    icon = appInfo.loadIcon(pm),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .distinctBy { it.packageName }
            .toList()
    }

    fun loadRoleCandidates(context: Context): Map<String, List<AppInfo>> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val result = LinkedHashMap<String, MutableList<AppInfo>>()

        fun collect(category: String, intent: Intent) {
            val resolveInfos = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }

            for (ri in resolveInfos) {
                val activityInfo = ri.activityInfo ?: continue
                val packageName = activityInfo.packageName
                if (packageName == selfPackage) continue

                val appInfo = AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = packageName,
                    activityName = activityInfo.name,
                    icon = ri.loadIcon(pm),
                    isSystemApp = isSystemPackage(pm, packageName)
                )

                val bucket = result.getOrPut(category) { mutableListOf() }
                if (bucket.none { it.packageName == appInfo.packageName }) {
                    bucket.add(appInfo)
                }
            }
        }

        collect("phone", Intent(Intent.ACTION_DIAL))
        collect("messages", Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        collect("camera", Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        collect("contacts", Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))

        return result
    }

    private fun isSystemPackage(pm: PackageManager, packageName: String): Boolean {
        return try {
            (pm.getApplicationInfo(packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }
}
