package com.nihalthakral.nihalhome

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageStore(context: Context) {

    private val prefs = context.getSharedPreferences("app_usage_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun recordLaunch(packageName: String) {
        applyDailyDecayIfNeeded()
        val current = prefs.getFloat(scoreKey(packageName), 0f)
        prefs.edit().putFloat(scoreKey(packageName), current + 1f).apply()
    }

    fun getTopPackages(limit: Int): List<String> {
        applyDailyDecayIfNeeded()
        return prefs.all
            .asSequence()
            .filter { it.key.startsWith(SCORE_PREFIX) }
            .mapNotNull { (key, value) ->
                val score = value as? Float ?: return@mapNotNull null
                if (score <= 0f) return@mapNotNull null
                key.removePrefix(SCORE_PREFIX) to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun applyDailyDecayIfNeeded() {
        val today = dateFormat.format(Date())
        val lastDecayDate = prefs.getString(KEY_LAST_DECAY, null)
        if (lastDecayDate == today) return

        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(SCORE_PREFIX) && value is Float) {
                val decayed = value * DECAY_FACTOR
                editor.putFloat(key, if (decayed < MIN_SCORE) 0f else decayed)
            }
        }
        editor.putString(KEY_LAST_DECAY, today)
        editor.apply()
    }

    private fun scoreKey(packageName: String) = SCORE_PREFIX + packageName

    companion object {
        private const val SCORE_PREFIX = "score_"
        private const val KEY_LAST_DECAY = "last_decay_date"
        private const val DECAY_FACTOR = 0.9f
        private const val MIN_SCORE = 0.05f
        const val MAX_FREQUENT_APPS = 8
    }
}
