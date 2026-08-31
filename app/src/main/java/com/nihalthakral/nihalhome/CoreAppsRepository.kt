package com.nihalthakral.nihalhome

object CoreAppsRepository {

    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9 ]")
    private val EXTRA_WHITESPACE_REGEX = Regex("\\s+")

    private class CoreAppCategory(
        val key: String,
        aliases: List<String>
    ) {
        val normalizedAliases: List<String> = aliases.map { normalize(it) }
    }

    private val CATEGORIES = listOf(
        CoreAppCategory(
            key = "phone",
            aliases = listOf(
                "phone", "dialer", "calls", "call", "phone dialer",
                "contacts & dialer", "telephone"
            )
        ),
        CoreAppCategory(
            key = "messages",
            aliases = listOf(
                "messages", "message", "messaging", "sms", "text", "texts",
                "sms messenger", "textra", "messages sms"
            )
        ),
        CoreAppCategory(
            key = "camera",
            aliases = listOf("camera", "cam")
        ),
        CoreAppCategory(
            key = "gallery",
            aliases = listOf(
                "gallery", "photos", "photo", "images", "google photos",
                "media", "album", "albums", "picture", "pictures"
            )
        ),
        CoreAppCategory(
            key = "clock",
            aliases = listOf("clock", "alarm", "alarms", "clock & alarm", "deskclock")
        ),
        CoreAppCategory(
            key = "calculator",
            aliases = listOf("calculator", "calc")
        ),
        CoreAppCategory(
            key = "files",
            aliases = listOf(
                "files", "file manager", "my files", "file explorer",
                "files by google", "explorer", "file", "filemanager"
            )
        ),
        CoreAppCategory(
            key = "settings",
            aliases = listOf("settings", "setting", "system settings")
        )
    )

    // Categories where an OEM system app can be hidden from the launcher list
    // and where a user-installed app (e.g. Truecaller) can otherwise win the
    // slot by label alone. For these, a launcher-hidden system role candidate
    // (see AppRepository.loadRoleCandidates) is preferred whenever one exists.
    private val ROLE_PREFERRED_CATEGORIES = setOf("phone", "messages", "camera", "contacts")

    /**
     * @param apps apps to run label-matching against (should include hidden/
     *   non-launcher apps, e.g. AppRepository.loadAllInstalledApps).
     * @param roleCandidates output of AppRepository.loadRoleCandidates(context),
     *   used to recover the real system Phone/Messages/Camera/Contacts app on
     *   OEMs that hide its launcher-visible activity.
     */
    fun detectCoreApps(
        apps: List<AppInfo>,
        roleCandidates: Map<String, List<AppInfo>> = emptyMap()
    ): List<AppInfo> {
        if (apps.isEmpty() && roleCandidates.isEmpty()) return emptyList()

        val usedPackages = mutableSetOf<String>()
        val result = mutableListOf<AppInfo>()

        for (category in CATEGORIES) {
            val match = resolveCategory(apps, category, roleCandidates, usedPackages) ?: continue
            usedPackages.add(match.packageName)
            result.add(match)
        }

        return result
    }

    private fun resolveCategory(
        apps: List<AppInfo>,
        category: CoreAppCategory,
        roleCandidates: Map<String, List<AppInfo>>,
        usedPackages: Set<String>
    ): AppInfo? {
        val labelMatch = findBestMatch(apps, category, usedPackages)

        if (category.key !in ROLE_PREFERRED_CATEGORIES) {
            return labelMatch
        }

        val candidates = roleCandidates[category.key].orEmpty()
            .filter { it.packageName !in usedPackages }

        // Prefer a system-flagged role candidate over anything else: this is
        // the real OEM Phone/Messages/Camera/Contacts app, even if its
        // activity is hidden from the launcher list and even if a
        // user-installed app (e.g. Truecaller) matches the label better.
        val systemRoleMatch = candidates.firstOrNull { it.isSystemApp }
        if (systemRoleMatch != null) return systemRoleMatch

        // No hidden system app found via role query. If the label match we
        // already found is itself a system app, trust it.
        if (labelMatch != null && labelMatch.isSystemApp) return labelMatch

        // Otherwise prefer any label match (covers normal devices where the
        // real app is simply named "Phone"/"Camera" and is launcher-visible).
        if (labelMatch != null) return labelMatch

        // Last resort: any role candidate at all (even non-system), so the
        // slot isn't left empty on very unusual OEM setups.
        return candidates.firstOrNull()
    }

    private fun findBestMatch(
        apps: List<AppInfo>,
        category: CoreAppCategory,
        usedPackages: Set<String>
    ): AppInfo? {
        var bestApp: AppInfo? = null
        var bestScore = Int.MAX_VALUE

        for (app in apps) {
            if (app.packageName in usedPackages) continue

            val normalizedLabel = normalize(app.label)
            if (normalizedLabel.isEmpty()) continue

            for ((aliasIndex, normalizedAlias) in category.normalizedAliases.withIndex()) {
                if (normalizedAlias.isEmpty()) continue

                if (normalizedLabel != normalizedAlias) continue

                val score = aliasIndex
                if (score < bestScore) {
                    bestScore = score
                    bestApp = app
                }
            }
        }

        return bestApp
    }

    private fun normalize(input: String): String {
        return input
            .lowercase()
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .replace(EXTRA_WHITESPACE_REGEX, " ")
            .trim()
    }
}
