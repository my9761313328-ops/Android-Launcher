package com.nihalthakral.nihalhome

import android.app.Application
import android.content.Intent
import android.os.Process
import kotlin.system.exitProcess

class LauncherApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                applicationContext.startActivity(intent)
            } catch (ignored: Throwable) {
            }

            defaultHandler?.uncaughtException(thread, throwable)
                ?: run {
                    Process.killProcess(Process.myPid())
                    exitProcess(1)
                }
        }
    }
}
