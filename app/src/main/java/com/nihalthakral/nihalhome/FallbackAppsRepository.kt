package com.nihalthakral.nihalhome

object FallbackAppsRepository {

    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9 ]")
    private val EXTRA_WHITESPACE_REGEX = Regex("\\s+")

    private class FallbackApp(
        val key: String,
        aliases: List<String>
    ) {
        val normalizedAliases: List<String> = aliases.map { normalize(it) }
    }

    private val POPULAR_APPS = listOf(
        FallbackApp("whatsapp", listOf("whatsapp", "whats app")),
        FallbackApp("instagram", listOf("instagram", "insta")),
        FallbackApp("truecaller", listOf("truecaller", "true caller")),
        FallbackApp("chrome", listOf("chrome", "google chrome")),
        FallbackApp("snapchat", listOf("snapchat", "snap chat")),
        FallbackApp("youtube", listOf("youtube", "you tube")),
        FallbackApp("phonepe", listOf("phonepe", "phone pe")),
        FallbackApp("telegram", listOf("telegram")),
        FallbackApp("facebook", listOf("facebook", "fb")),
        FallbackApp("paytm", listOf("paytm")),
        FallbackApp("spotify", listOf("spotify")),
        FallbackApp("zomato", listOf("zomato")),
        FallbackApp("swiggy", listOf("swiggy")),
        FallbackApp("amazon", listOf("amazon", "amazon shopping")),
        FallbackApp("flipkart", listOf("flipkart")),
        FallbackApp(
            "jiohotstar",
            listOf("jiohotstar", "jio hotstar", "hotstar", "disney hotstar", "disney+ hotstar")
        ),
        FallbackApp("netflix", listOf("netflix")),
        FallbackApp("freefire", listOf("free fire", "freefire", "garena free fire")),
        FallbackApp("bgmi", listOf("bgmi", "battlegrounds mobile india"))
    )

    fun detectFallbackApps(
        apps: List<AppInfo>,
        excludePackages: Set<String> = emptySet()
    ): List<AppInfo> {
        if (apps.isEmpty()) return emptyList()

        val usedPackages = excludePackages.toMutableSet()
        val result = mutableListOf<AppInfo>()

        for (popular in POPULAR_APPS) {
            val match = findBestMatch(apps, popular, usedPackages) ?: continue
            usedPackages.add(match.packageName)
            result.add(match)
        }

        return result
    }

    private fun findBestMatch(
        apps: List<AppInfo>,
        popular: FallbackApp,
        usedPackages: Set<String>
    ): AppInfo? {
        var bestApp: AppInfo? = null
        var bestScore = Int.MAX_VALUE

        for (app in apps) {
            if (app.packageName in usedPackages) continue

            val normalizedLabel = normalize(app.label)
            if (normalizedLabel.isEmpty()) continue

            for ((aliasIndex, normalizedAlias) in popular.normalizedAliases.withIndex()) {
                if (normalizedAlias.isEmpty()) continue

                val tier = when {
                    normalizedLabel == normalizedAlias -> 0
                    containsWholeWord(normalizedLabel, normalizedAlias) -> 1
                    normalizedLabel.contains(normalizedAlias) -> 2
                    normalizedAlias.contains(normalizedLabel) -> 3
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
