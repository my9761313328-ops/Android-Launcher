package com.nihalthakral.nihalhome

import android.content.Context

class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("fav_apps_prefs", Context.MODE_PRIVATE)

    fun isFavorite(packageName: String): Boolean =
        readOrderedFavorites().contains(packageName)

    /**
     * Toggles the favorite state of [packageName].
     * Favorites are kept in the order they were added (oldest first),
     * NOT alphabetically - newly favorited apps are appended to the end.
     * @return true if the app is now a favorite, false if it was removed.
     */
    fun toggleFavorite(packageName: String): Boolean {
        val current = readOrderedFavorites().toMutableList()
        val nowFavorite = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        writeOrderedFavorites(current)
        return nowFavorite
    }

    fun getFavoritePackages(): Set<String> = readOrderedFavorites().toSet()

    /**
     * Same favorites as [getFavoritePackages] but preserving the order in
     * which they were added (oldest favorited first).
     */
    fun getFavoritePackagesOrdered(): List<String> = readOrderedFavorites()

    private fun readOrderedFavorites(): List<String> {
        val stored = prefs.getString(KEY_FAVORITES_ORDER, null)
        if (stored != null) {
            return if (stored.isEmpty()) emptyList() else stored.split(DELIMITER)
        }

        // Migrate from the old unordered Set-based storage (no ordering info
        // available yet, so fall back to whatever order the Set gives us).
        val legacySet = prefs.getStringSet(KEY_FAVORITES_LEGACY, null)
        val migrated = legacySet?.toList() ?: emptyList()
        writeOrderedFavorites(migrated)
        return migrated
    }

    private fun writeOrderedFavorites(packages: List<String>) {
        prefs.edit()
            .putString(KEY_FAVORITES_ORDER, packages.joinToString(DELIMITER))
            .remove(KEY_FAVORITES_LEGACY)
            .apply()
    }

    companion object {
        private const val KEY_FAVORITES_LEGACY = "favorite_packages"
        private const val KEY_FAVORITES_ORDER = "favorite_packages_order"
        private const val DELIMITER = "\u0001"
    }
}
