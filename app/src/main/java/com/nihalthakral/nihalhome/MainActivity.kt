package com.nihalthakral.nihalhome

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        findViewById<Button>(R.id.buttonGo).setOnClickListener {
            onGoClicked()
        }
    }

    private fun onGoClicked() {
        val prefs = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)
        val dontAskAgain = prefs.getBoolean(PreferenceKeys.KEY_DONT_ASK_AGAIN, false)
        val savedPackage = prefs.getString(PreferenceKeys.KEY_SAVED_LAUNCHER_PACKAGE, null)
        val savedActivity = prefs.getString(PreferenceKeys.KEY_SAVED_LAUNCHER_CLASS, null)

        if (dontAskAgain && savedPackage != null && savedActivity != null) {
            val launched = LauncherUtils.launchSelected(this, savedPackage, savedActivity)
            if (!launched) {
                showChooseLauncherDialog()
            }
        } else {
            showChooseLauncherDialog()
        }
    }

    private fun showChooseLauncherDialog() {
        val launchers = LauncherUtils.getAvailableLaunchers(this)

        val dialog = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_choose_launcher, null)
        dialog.setContentView(sheetView)

        val listContainer = sheetView.findViewById<LinearLayout>(R.id.launcherListContainer)
        val checkboxDontAskAgain = sheetView.findViewById<CheckBox>(R.id.checkboxDontAskAgain)
        val buttonLaunchIt = sheetView.findViewById<Button>(R.id.buttonLaunchIt)

        var selectedLauncher: LauncherAppInfo? = null
        var selectedRow: View? = null

        for (launcherInfo in launchers) {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_launcher, listContainer, false)

            itemView.findViewById<ImageView>(R.id.imageLauncherIcon).setImageDrawable(launcherInfo.icon)
            itemView.findViewById<TextView>(R.id.textLauncherLabel).text = launcherInfo.label
            val imageSelected = itemView.findViewById<ImageView>(R.id.imageLauncherSelected)

            itemView.setOnClickListener {
                selectedRow?.findViewById<ImageView>(R.id.imageLauncherSelected)?.visibility = View.INVISIBLE
                imageSelected.visibility = View.VISIBLE
                selectedRow = itemView
                selectedLauncher = launcherInfo
                buttonLaunchIt.isEnabled = true
                buttonLaunchIt.setBackgroundColor(ContextCompat.getColor(this, R.color.onboarding_accent))
            }

            listContainer.addView(itemView)
        }

        buttonLaunchIt.setOnClickListener {
            val chosen = selectedLauncher ?: return@setOnClickListener
            val prefs = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE)

            if (checkboxDontAskAgain.isChecked) {
                prefs.edit()
                    .putBoolean(PreferenceKeys.KEY_DONT_ASK_AGAIN, true)
                    .putString(PreferenceKeys.KEY_SAVED_LAUNCHER_PACKAGE, chosen.packageName)
                    .putString(PreferenceKeys.KEY_SAVED_LAUNCHER_CLASS, chosen.activityName)
                    .apply()
            } else {
                prefs.edit()
                    .putBoolean(PreferenceKeys.KEY_DONT_ASK_AGAIN, false)
                    .apply()
            }

            LauncherUtils.launchSelected(this, chosen.packageName, chosen.activityName)
            dialog.dismiss()
        }

        dialog.show()
    }
}
