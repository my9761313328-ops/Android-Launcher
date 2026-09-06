package com.nihalthakral.nihalhome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)
        val languageSelected = prefs.contains(PreferenceKeys.KEY_SELECTED_LANGUAGE)

        if (!languageSelected) {
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
            finish()
            return
        }

        if (!LauncherUtils.isDefaultLauncher(this)) {
            startActivity(Intent(this, DefaultLauncherActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // On the launcher's home screen, back press should do nothing
        // (prevents falling through to any other/background launcher).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally do nothing.
            }
        })
    }
}
