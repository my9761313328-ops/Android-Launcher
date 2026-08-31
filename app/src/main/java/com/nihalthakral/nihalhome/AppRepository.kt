package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object AppRepository {

    private const val TAG = "AppRepository"

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val result = LinkedHashMap<String, AppInfo>()

        // Primary query: standard launcher-category resolution.
        // On some OEM skins (Samsung, MIUI, etc.) certain stock apps like
        // Phone/Dialer ship with their launcher activity component in a
        // "disabled until used" state, which a flags=0 query silently drops.
        // MATCH_DISABLED_COMPONENTS (and DISABLED_UNTIL_USED_COMPONENTS on
        // newer APIs) forces those back into the results.
        val queryFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        } else {
            PackageManager.MATCH_DISABLED_COMPONENTS
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            pm.queryIntentActivities(launcherIntent, queryFlags)
        } catch (e: Exception) {
            Log.w(TAG, "queryIntentActivities failed, falling back to flags=0", e)
            try {
                pm.queryIntentActivities(launcherIntent, 0)
            } catch (e2: Exception) {
                emptyList()
            }
        }

        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName
            if (pkg == selfPackage) continue
            if (result.containsKey(pkg)) continue
            result[pkg] = AppInfo(
                label = safeLoadLabel(ri, pm, pkg),
                packageName = pkg,
                activityName = ri.activityInfo.name,
                icon = safeLoadIcon(ri, pm, context)
            )
        }

        // Safety-net pass: some OEM system apps (Phone, Messages, Contacts,
        // etc.) don't get picked up by queryIntentActivities at all on
        // certain skins/API levels, even with the extra flags above. For any
        // installed package we haven't already resolved, ask the system for
        // its normal launch intent directly and use that instead. This is
        // the same lookup Android itself uses when you tap an app icon, so
        // if it works there, it works here too — independent of brand.
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName
                if (pkg == selfPackage || result.containsKey(pkg)) continue

                val launchIntent = try {
                    pm.getLaunchIntentForPackage(pkg)
                } catch (e: Exception) {
                    null
                } ?: continue

                val component = launchIntent.component ?: continue

                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (e: Exception) {
                    continue
                }

                result[pkg] = AppInfo(
                    label = label,
                    packageName = pkg,
                    activityName = component.className,
                    icon = icon
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "getInstalledApplications fallback failed", e)
        }

        return result.values
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun safeLoadLabel(ri: android.content.pm.ResolveInfo, pm: PackageManager, fallback: String): String {
        return try {
            ri.loadLabel(pm).toString()
        } catch (e: Exception) {
            fallback
        }
    }

    private fun safeLoadIcon(ri: android.content.pm.ResolveInfo, pm: PackageManager, context: Context): android.graphics.drawable.Drawable {
        return try {
            ri.loadIcon(pm)
        } catch (e: Exception) {
            context.packageManager.defaultActivityIcon
        }
    }
}
