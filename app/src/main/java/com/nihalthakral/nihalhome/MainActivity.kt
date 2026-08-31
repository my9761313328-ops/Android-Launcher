package com.nihalthakral.nihalhome

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.MobileAds
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var usageStore: UsageStore

    private var allApps: List<AppInfo> = emptyList()
    private lateinit var frequentAdapter: FrequentAppsAdapter
    private lateinit var allAppsAdapter: AllAppsAdapter
    private lateinit var coreAppsAdapter: FrequentAppsAdapter

    private var homeScreenReady = false
    private var searchInput: EditText? = null
    private var allAppsRecycler: RecyclerView? = null
    private var frequentRecycler: RecyclerView? = null
    private var coreAppsRecycler: RecyclerView? = null
    private var contentScroll: NestedScrollView? = null
    private var nativeAdManager: NativeAdManager? = null

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTicker = object : Runnable {
        override fun run() {
            updateGreetingAndDate()

            val delay = 1000 - (System.currentTimeMillis() % 1000)
            clockHandler.postDelayed(this, delay)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ScreenAdapter.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("nihal_home_prefs", MODE_PRIVATE)
        usageStore = UsageStore(this)
        MobileAds.initialize(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                resetUIState()
            }
        })

        renderCorrectScreen()
    }

    private var imeVisible = false
    private var pendingLaunch: AppInfo? = null
    private var activeContextPopup: android.widget.PopupWindow? = null

    private var pullDownTracking = false
    private var pullDownStartY = 0f
    private val pullDownThresholdPx: Float by lazy { 60 * resources.displayMetrics.density }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        val popup = activeContextPopup
        if (popup != null && popup.isShowing) {

            return true
        }

        if (homeScreenReady) {
            val scroll = contentScroll
            when (ev.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    pullDownStartY = ev.y
                    pullDownTracking = scroll != null && !scroll.canScrollVertically(-1)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (pullDownTracking) {
                        val dy = ev.y - pullDownStartY
                        if (dy > pullDownThresholdPx) {
                            pullDownTracking = false
                            expandNotificationPanel()
                        } else if (dy < 0) {

                            pullDownTracking = false
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    pullDownTracking = false
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {

        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            val input = searchInput
            if (input != null && input.hasFocus()) {
                input.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(input.windowToken, 0)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean(KEY_SETUP_DONE, false)) {
            refreshApps()
            resetUIState()
            updateGreetingAndDate()
            clockHandler.removeCallbacks(clockTicker)
            clockHandler.post(clockTicker)
            updateSponsoredSection()
        }
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockTicker)
        nativeAdManager?.destroy()
    }

    private fun updateSponsoredSection() {
        if (NetworkUtils.isOnline(this)) {
            nativeAdManager?.refresh()
        } else {
            nativeAdManager?.showOfflineFallback()
        }
        updateSponsoredLabel()
    }

    private fun updateSponsoredLabel() {
        val label = findViewById<TextView>(R.id.sponsoredLabel)
        label.text = when {
            nativeAdManager?.isOfflineFallbackVisible() == true -> "Offline Game • For You"
            nativeAdManager?.isShimmerVisible() == true -> "Sponsorship Not Found"
            nativeAdManager?.isFailedVisible() == true -> "Sponsored • Error: ${nativeAdManager?.getLastError()}"
            else -> "Sponsored • For You"
        }
    }

    private fun resetUIState() {
        if (!homeScreenReady) return

        val input = searchInput ?: return
        if (input.text?.isNotEmpty() == true) {
            input.setText("")
        }
        input.clearFocus()

        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(input.windowToken, 0)

        allAppsRecycler?.scrollToPosition(0)
        frequentRecycler?.scrollToPosition(0)
        contentScroll?.post { contentScroll?.smoothScrollTo(0, 0) }
    }

    private fun renderCorrectScreen() {
        val setupDone = prefs.getBoolean(KEY_SETUP_DONE, false)
        if (setupDone) {
            setupHomeScreen()
        } else {
            setContentView(R.layout.activity_setup)
            findViewById<Button>(R.id.easySetupButton).setOnClickListener {
                openDefaultLauncherPicker()
                markSetupDone()
                setupHomeScreen()
            }
        }
    }

    private fun setupHomeScreen() {
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val imeNowVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible && !imeNowVisible) {
                resetUIState()
                val app = pendingLaunch
                pendingLaunch = null
                if (app != null) {
                    performLaunch(app)
                }
            }
            imeVisible = imeNowVisible
            insets
        }

        contentScroll = findViewById(R.id.contentScroll)
        val allAppsRecyclerView = findViewById<RecyclerView>(R.id.allAppsRecycler)
        val frequentRecyclerView = findViewById<RecyclerView>(R.id.frequentRecycler)
        allAppsRecycler = allAppsRecyclerView
        frequentRecycler = frequentRecyclerView

        allAppsRecyclerView.itemAnimator = null
        frequentRecyclerView.itemAnimator = null
        allAppsRecyclerView.setHasFixedSize(false)
        frequentRecyclerView.setHasFixedSize(false)

        allAppsAdapter = AllAppsAdapter(
            onClick = { launchApp(it) },
            onLongClick = { app, view -> showAppContextMenu(app, view) }
        )
        allAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        allAppsRecyclerView.adapter = allAppsAdapter

        frequentAdapter = FrequentAppsAdapter(
            onClick = { launchApp(it) },
            onLongClick = { app, view -> showAppContextMenu(app, view) }
        )
        frequentRecyclerView.layoutManager = GridLayoutManager(this, 4)
        frequentRecyclerView.adapter = frequentAdapter

        val coreAppsRecyclerView = findViewById<RecyclerView>(R.id.coreAppsRecycler)
        coreAppsRecycler = coreAppsRecyclerView
        coreAppsRecyclerView.itemAnimator = null
        coreAppsRecyclerView.setHasFixedSize(false)
        coreAppsAdapter = FrequentAppsAdapter(
            onClick = { launchApp(it) },
            onLongClick = { app, view -> showAppContextMenu(app, view) }
        )
        coreAppsRecyclerView.layoutManager = GridLayoutManager(this, 4)
        coreAppsRecyclerView.adapter = coreAppsAdapter

        val sponsoredContainer = findViewById<FrameLayout>(R.id.sponsoredContainer)
        nativeAdManager = NativeAdManager(applicationContext, sponsoredContainer) {
            updateSponsoredLabel()
        }
        updateSponsoredSection()

        setupSearch()
        updateGreetingAndDate()
        refreshApps()
        homeScreenReady = true
    }

    private fun expandNotificationPanel() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = statusBarManager.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (e: Exception) {

        }
    }

    private fun setupSearch() {
        val searchInput = findViewById<EditText>(R.id.searchInput)
        this.searchInput = searchInput
        val clearIcon = findViewById<TextView>(R.id.clearIcon)
        val normalContent = findViewById<View>(R.id.normalContent)
        val searchBarContainer = findViewById<View>(R.id.searchBarContainer)

        searchBarContainer.setOnClickListener {
            searchInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                clearIcon.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                normalContent.visibility = if (query.isEmpty()) View.VISIBLE else View.GONE
                applyFilter(query)
            }
        })

        clearIcon.setOnClickListener {
            searchInput.setText("")
        }
    }

    private fun refreshApps() {
        allApps = AppRepository.loadApps(this)
        applyFilter((findViewById<EditText>(R.id.searchInput)).text?.toString().orEmpty())

        val coreApps = try {
            CoreAppsRepository.detectCoreApps(allApps)
        } catch (e: Exception) {
            emptyList()
        }
        updateFrequentApps(coreApps)
        updateCoreApps(coreApps)
    }

    private fun applyFilter(query: String) {
        val trimmed = query.trim()
        val emptyState = findViewById<View>(R.id.emptyState)

        val filtered = if (trimmed.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.contains(trimmed, ignoreCase = true) }
        }

        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        allAppsAdapter.submit(filtered, grouped = trimmed.isEmpty())
    }

    private fun updateFrequentApps(coreApps: List<AppInfo>) {
        val usedPackages = mutableSetOf<String>()
        usedPackages.addAll(coreApps.map { it.packageName })
        val result = mutableListOf<AppInfo>()

        val topPackages = usageStore.getTopPackages(UsageStore.MAX_FREQUENT_APPS)
        val appsByPackage = allApps.associateBy { it.packageName }
        val usageApps = topPackages
            .mapNotNull { appsByPackage[it] }
            .filter { it.packageName !in usedPackages }
        result.addAll(usageApps)
        usedPackages.addAll(usageApps.map { it.packageName })

        if (result.size < UsageStore.MAX_FREQUENT_APPS) {
            val remaining = UsageStore.MAX_FREQUENT_APPS - result.size
            val fallbackApps = try {
                FallbackAppsRepository.detectFallbackApps(allApps, usedPackages)
            } catch (e: Exception) {
                emptyList()
            }.take(remaining)
            result.addAll(fallbackApps)
            usedPackages.addAll(fallbackApps.map { it.packageName })
        }

        if (result.size < UsageStore.MAX_FREQUENT_APPS) {
            val remaining = UsageStore.MAX_FREQUENT_APPS - result.size
            val randomApps = allApps
                .filter { it.packageName !in usedPackages }
                .shuffled()
                .take(remaining)
            result.addAll(randomApps)
        }

        val frequentSection = findViewById<View>(R.id.frequentSection)
        frequentSection.visibility = if (result.isEmpty()) View.GONE else View.VISIBLE
        frequentAdapter.submitList(result)
    }

    private fun updateCoreApps(coreApps: List<AppInfo>) {
        val coreAppsSection = findViewById<View>(R.id.coreAppsSection)
        coreAppsSection.visibility = if (coreApps.isEmpty()) View.GONE else View.VISIBLE
        coreAppsAdapter.submitList(coreApps)
    }

    private fun launchApp(app: AppInfo) {
        val input = searchInput
        if (imeVisible && input != null) {

            pendingLaunch = app
            input.clearFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(input.windowToken, 0)
            return
        }
        performLaunch(app)
    }

    private fun performLaunch(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = android.content.ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
            usageStore.recordLaunch(app.packageName)
            val coreApps = try {
                CoreAppsRepository.detectCoreApps(allApps)
            } catch (e: Exception) {
                emptyList()
            }
            updateFrequentApps(coreApps)
        } catch (e: Exception) {

        }
    }

    private fun showAppContextMenu(app: AppInfo, anchor: View) {
        val inflater = android.view.LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.popup_app_context, null)

        popupView.findViewById<TextView>(R.id.contextAppName).text = app.label

        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                elevation = 12f
            }
            setOnDismissListener {
                if (activeContextPopup === this) {
                    activeContextPopup = null
                }
            }
        }
        activeContextPopup = popupWindow

        popupView.findViewById<View>(R.id.appInfoRow).setOnClickListener {
            popupWindow.dismiss()
            openAppInfo(app)
        }

        popupView.measure(
            android.view.View.MeasureSpec.UNSPECIFIED,
            android.view.View.MeasureSpec.UNSPECIFIED
        )
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight
        val marginPx = (8 * resources.displayMetrics.density).toInt()
        val gapPx = (4 * resources.displayMetrics.density).toInt()

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val anchorX = anchorLocation[0]
        val anchorY = anchorLocation[1]
        val anchorBottom = anchorY + anchor.height

        val spaceBelow = screenHeight - anchorBottom
        val openAbove = spaceBelow < popupHeight + marginPx

        val y = if (openAbove) {
            anchorY - popupHeight - gapPx
        } else {
            anchorBottom + gapPx
        }

        val rawX = anchorX + (anchor.width - popupWidth) / 2
        val x = rawX.coerceIn(marginPx, (screenWidth - popupWidth - marginPx).coerceAtLeast(marginPx))

        popupWindow.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y)
    }

    private fun openAppInfo(app: AppInfo) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", app.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {

        }
    }

    private fun updateGreetingAndDate() {
        val greeting = findViewById<TextView>(R.id.greetingText)
        val dateText = findViewById<TextView>(R.id.dateText)

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val emoji = when (hour) {
            in 4..11 -> "🌄"
            in 12..16 -> "🌤️"
            in 17..19 -> "🌇"
            else -> "🌃"
        }

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        greeting.text = "${timeFormat.format(calendar.time)} $emoji"

        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        dateText.text = dateFormat.format(calendar.time)
    }

    private fun markSetupDone() {
        prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply()
    }

    private fun openDefaultLauncherPicker() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                startActivity(homeIntent)
            } catch (e2: Exception) {
            }
        }
    }

    companion object {
        private const val KEY_SETUP_DONE = "setup_done"
    }
}
