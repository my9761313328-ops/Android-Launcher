package com.nihalthakral.nihalhome

import android.content.Context

object LocalizationHelper {
    fun isHindiSelected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val language = prefs.getString(PreferenceKeys.KEY_SELECTED_LANGUAGE, PreferenceKeys.LANGUAGE_ENGLISH)
        return language == PreferenceKeys.LANGUAGE_HINDI_URDU
    }
}
