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

    private val ROLE_PREFERRED_CATEGORIES = setOf("phone", "messages", "camera", "contacts")

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

        if (labelMatch != null && labelMatch.isSystemApp) return labelMatch

        val systemRoleMatch = candidates.firstOrNull { it.isSystemApp }
        if (systemRoleMatch != null) return systemRoleMatch

        if (labelMatch != null) return labelMatch

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
