package com.nihalthakral.nihalhome

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

object UpdateChecker {

    const val VERSION_URL = "https://backend-nihalhome.github.io/version.txt"
    const val APK_URL = "https://backend-nihalhome.github.io/app-debug.apk"
    const val APK_FILE_NAME = "update.apk"
    const val PART_FILE_NAME = "update.apk.part"

    private const val NETWORK_TIMEOUT_MS = 3000

    @Volatile
    var updateDismissed = false

    fun updateDirectory(context: Context): File {
        return File(context.cacheDir, "update_apk")
    }

    fun clearDownloadedFiles(context: Context) {
        updateDirectory(context).deleteRecursively()
    }

    fun hasPendingUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val pendingVersion = prefs.getInt(PreferenceKeys.KEY_PENDING_UPDATE_VERSION, 0)
        if (pendingVersion == 0) return false

        if (pendingVersion > currentVersionCode(context)) return true

        prefs.edit().remove(PreferenceKeys.KEY_PENDING_UPDATE_VERSION).apply()
        clearDownloadedFiles(context)
        return false
    }

    fun isCheckDue(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(PreferenceKeys.KEY_LAST_UPDATE_CHECK, 0L)
        val lastAttempt = prefs.getLong(PreferenceKeys.KEY_LAST_UPDATE_ATTEMPT, 0L)

        return hasElapsed(now, lastCheck, PreferenceKeys.UPDATE_CHECK_INTERVAL_MS) &&
            hasElapsed(now, lastAttempt, PreferenceKeys.UPDATE_RETRY_INTERVAL_MS)
    }

    private val checkRunning = AtomicBoolean(false)

    fun checkInBackground(context: Context) {
        if (!checkRunning.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread {
            try {
                checkForUpdate(appContext)
            } catch (e: Exception) {
            } finally {
                checkRunning.set(false)
            }
        }.start()
    }

    fun checkForUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit().putLong(PreferenceKeys.KEY_LAST_UPDATE_ATTEMPT, now).apply()

        val latestVersion = fetchLatestVersion() ?: return false
        prefs.edit().putLong(PreferenceKeys.KEY_LAST_UPDATE_CHECK, now).apply()

        if (latestVersion > currentVersionCode(context)) {
            prefs.edit().putInt(PreferenceKeys.KEY_PENDING_UPDATE_VERSION, latestVersion).apply()
            return true
        }

        prefs.edit().remove(PreferenceKeys.KEY_PENDING_UPDATE_VERSION).apply()
        return false
    }

    private fun hasElapsed(now: Long, last: Long, interval: Long): Boolean {
        return now < last || now - last >= interval
    }

    private fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info).toInt()
    }

    private fun fetchLatestVersion(): Int? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(VERSION_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            connection.useCaches = false

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull()
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
