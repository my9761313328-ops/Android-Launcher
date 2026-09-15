package com.nihalthakral.nihalhome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class OtpSmsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_sms)

        loadSmsPermissionApps()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToMainScreen()
            }
        })
    }

    private fun loadSmsPermissionApps() {
        val apps = SmsPermissionScanner.scan(applicationContext)

        val container = findViewById<LinearLayout>(R.id.containerSmsApps)
        val noAppsContainer = findViewById<LinearLayout>(R.id.containerSmsNoApps)
        container.removeAllViews()

        if (apps.isEmpty()) {
            noAppsContainer.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        noAppsContainer.visibility = View.GONE
        container.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)

        apps.forEach { app ->
            val row = inflater.inflate(R.layout.item_sms_app, container, false)

            row.findViewById<ImageView>(R.id.imageSmsAppIcon).setImageDrawable(app.icon)
            row.findViewById<TextView>(R.id.textSmsAppName).text = app.label

            val packageLabel = row.findViewById<TextView>(R.id.textSmsAppPackage)
            val actionButton = row.findViewById<android.widget.Button>(R.id.buttonAppInfo)

            if (app.isDefaultSmsHandler) {
                packageLabel.text = getString(R.string.otp_sms_default_handler_note)
                actionButton.text = getString(R.string.action_change_default)
                actionButton.setOnClickListener {
                    openDefaultAppsSettings()
                }
            } else {
                packageLabel.text = app.packageName
                actionButton.text = getString(R.string.action_app_info)
                actionButton.setOnClickListener {
                    openAppInfoSettings(app.packageName)
                }
            }

            container.addView(row)
        }
    }

    private fun openAppInfoSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            startActivity(intent)
        } catch (e: Exception) {

        }
    }

    private fun openDefaultAppsSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            } catch (e2: Exception) {

            }
        }
    }

    private fun goToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
