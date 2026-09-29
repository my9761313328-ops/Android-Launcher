package com.nihalthakral.nihalhome

object PreferenceKeys {
    const val PREFS_NAME = "nihal_home_prefs"
    const val KEY_SELECTED_LANGUAGE = "selected_language"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_HINDI_URDU = "hi_ur"
    const val KEY_DONT_ASK_AGAIN = "dont_ask_launcher_again"
    const val KEY_SAVED_LAUNCHER_PACKAGE = "saved_launcher_package"
    const val KEY_SAVED_LAUNCHER_CLASS = "saved_launcher_class"
    const val KEY_UNLOCK_EXPIRY_TIMESTAMP = "unlock_expiry_timestamp"
    const val UNLOCK_DURATION_MS = 24 * 60 * 60 * 1000L
    const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    const val KEY_LAST_UPDATE_ATTEMPT = "last_update_attempt"
    const val KEY_PENDING_UPDATE_VERSION = "pending_update_version"
    const val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    const val UPDATE_RETRY_INTERVAL_MS = 15 * 60 * 1000L
}
