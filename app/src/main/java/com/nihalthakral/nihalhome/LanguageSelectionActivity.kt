package com.nihalthakral.nihalhome

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class LanguageSelectionActivity : ComponentActivity() {

    private var selectedLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_selection)

        findViewById<TextView>(R.id.textChooseLanguage).fitTextToViewHeight()

        val buttonEnglish = findViewById<Button>(R.id.buttonEnglish)
        val buttonHindiUrdu = findViewById<Button>(R.id.buttonHindiUrdu)
        val buttonNext = findViewById<Button>(R.id.buttonNext)

        buttonEnglish.setOnClickListener {
            selectedLanguage = PreferenceKeys.LANGUAGE_ENGLISH
            updateSelectionState(buttonEnglish, buttonHindiUrdu, buttonNext)
        }

        buttonHindiUrdu.setOnClickListener {
            selectedLanguage = PreferenceKeys.LANGUAGE_HINDI_URDU
            updateSelectionState(buttonHindiUrdu, buttonEnglish, buttonNext)
        }

        buttonNext.setOnClickListener {
            val language = selectedLanguage
            if (language != null) {
                getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PreferenceKeys.KEY_SELECTED_LANGUAGE, language)
                    .apply()
                startActivity(Intent(this, DefaultLauncherActivity::class.java))
                finish()
            }
        }
    }

    private fun updateSelectionState(selected: Button, unselected: Button, next: Button) {
        selected.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_accent))
        selected.setTextColor(ContextCompat.getColor(this, R.color.onboarding_button_text))
        unselected.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_button_unselected))
        unselected.setTextColor(ContextCompat.getColor(this, R.color.onboarding_heading))
        next.isEnabled = true
        next.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_accent))
        next.setTextColor(ContextCompat.getColor(this, R.color.onboarding_button_text))
    }
}
