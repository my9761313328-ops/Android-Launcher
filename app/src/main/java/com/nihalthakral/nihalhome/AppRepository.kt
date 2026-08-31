package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent

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
                    icon = ri.loadIcon(pm)
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
                    icon = appInfo.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .toList()
    }
}
