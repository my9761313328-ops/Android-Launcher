package com.nihalthakral.nihalhome

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity

class OfflineGameActivity : ComponentActivity() {

    private var gameWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_game)

        val webView = findViewById<WebView>(R.id.offlineGameWebView)
        gameWebView = webView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.loadUrl("file:///android_asset/game/offline_game.html")
    }

    override fun onDestroy() {
        gameWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        gameWebView = null
        super.onDestroy()
    }
}
