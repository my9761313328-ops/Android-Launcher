package com.nihalthakral.nihalhome

import android.content.Context

class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("fav_apps_prefs", Context.MODE_PRIVATE)

    fun isFavorite(packageName: String): Boolean =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.contains(packageName) == true

    /**
     * Toggles the favorite state of [packageName].
     * @return true if the app is now a favorite, false if it was removed.
     */
    fun toggleFavorite(packageName: String): Boolean {
        val current = HashSet(prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet())
        val nowFavorite = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return nowFavorite
    }

    fun getFavoritePackages(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    companion object {
        private const val KEY_FAVORITES = "favorite_packages"
    }
}
