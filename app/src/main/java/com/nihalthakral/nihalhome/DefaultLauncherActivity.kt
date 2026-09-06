package com.nihalthakral.nihalhome

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class DefaultLauncherActivity : ComponentActivity() {

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        proceedIfDefault()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default_launcher)

        findViewById<Button>(R.id.buttonContinue).setOnClickListener {
            requestDefaultLauncher()
        }
    }

    override fun onResume() {
        super.onResume()
        proceedIfDefault()
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    proceedIfDefault()
                } else {
                    roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }
                return
            }
        }
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun proceedIfDefault() {
        if (LauncherUtils.isDefaultLauncher(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
