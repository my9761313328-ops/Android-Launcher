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

    /**
     * Loads every installed app on the device, without filtering by the
     * launcher (CATEGORY_LAUNCHER) intent. Used only for name-matching
     * purposes (Core Apps / Fallback Popular Apps detection), so apps that
     * don't expose a standard launcher-visible activity are still
     * considered. Each returned app is guaranteed to have a valid launch
     * intent (via getLaunchIntentForPackage), so it can still be opened
     * normally if matched and tapped.
     */
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

    /**
     * Finds apps that can handle a *role* (dial a number, send an SMS, open
     * a contact, capture a photo) via the exact Android action OEMs
     * themselves use to trigger these apps internally.
     *
     * This deliberately does NOT require CATEGORY_LAUNCHER, because on many
     * OEM builds (MIUI, some Samsung/AOSP-derivative skins) the real system
     * Phone/Contacts app hides its main activity from the launcher list but
     * still responds to these standard actions. This is the same mechanism
     * Android itself (and other launchers) use to resolve "who handles
     * calling" independent of whether that app chooses to show a launcher
     * icon.
     *
     * Results are grouped by category key ("phone", "messages", "camera",
     * "contacts") and each entry carries isSystemApp so callers can prefer
     * the real OEM/system app over a user-installed app (e.g. Truecaller)
     * that also happens to answer the same action.
     */
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

        // Real dialer, even if its launcher icon is hidden by the OEM.
        collect("phone", Intent(Intent.ACTION_DIAL))

        // Real SMS/messaging app.
        collect("messages", Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))

        // Real camera app.
        collect("camera", Intent(MediaStore.ACTION_IMAGE_CAPTURE))

        // Real contacts app.
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
