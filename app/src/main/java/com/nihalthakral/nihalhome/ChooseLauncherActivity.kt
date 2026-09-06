package com.nihalthakral.nihalhome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class ChooseLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_launcher)

        val launchers = LauncherUtils.getAvailableLaunchers(this)

        val listContainer = findViewById<LinearLayout>(R.id.launcherListContainer)
        val checkboxDontAskAgain = findViewById<CheckBox>(R.id.checkboxDontAskAgain)
        val buttonLaunchIt = findViewById<Button>(R.id.buttonLaunchIt)

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
            finish()
        }
    }
}
