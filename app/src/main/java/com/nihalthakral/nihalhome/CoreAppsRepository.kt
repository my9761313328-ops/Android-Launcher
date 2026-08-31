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

    fun detectCoreApps(apps: List<AppInfo>): List<AppInfo> {
        if (apps.isEmpty()) return emptyList()

        val usedPackages = mutableSetOf<String>()
        val result = mutableListOf<AppInfo>()

        for (category in CATEGORIES) {
            val match = findBestMatch(apps, category, usedPackages) ?: continue
            usedPackages.add(match.packageName)
            result.add(match)
        }

        return result
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

                val tier = when {
                    normalizedLabel == normalizedAlias -> 0
                    containsWholeWord(normalizedLabel, normalizedAlias) -> 1
                    else -> continue
                }

                val score = tier * 1000 + aliasIndex
                if (score < bestScore) {
                    bestScore = score
                    bestApp = app
                }
            }
        }

        return bestApp
    }

    private fun containsWholeWord(text: String, word: String): Boolean {
        val words = text.split(" ")
        return words.any { it == word }
    }

    private fun normalize(input: String): String {
        return input
            .lowercase()
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .replace(EXTRA_WHITESPACE_REGEX, " ")
            .trim()
    }
}
