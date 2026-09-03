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
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.MobileAds
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var usageStore: UsageStore

    private var allApps: List<AppInfo> = emptyList()
    private lateinit var frequentAdapter: FrequentAppsAdapter
    private lateinit var allAppsAdapter: AllAppsAdapter
    private lateinit var coreAppsAdapter: CoreAppsAdapter

    private var homeScreenReady = false
    private var searchInput: EditText? = null
    private var searchIcon: View? = null
    private var clearIcon: View? = null
    private var searchBarContainer: ViewGroup? = null
    private var searchBarSticky: ViewGroup? = null
    private var isSearchBarPinned = false
    private var allAppsRecycler: RecyclerView? = null
    private var frequentRecycler: RecyclerView? = null
    private var coreAppsRecycler: RecyclerView? = null
    private var contentScroll: NestedScrollView? = null
    private var nativeAdManager: NativeAdManager? = null

    // --- Home-screen-like idle overlay ---
    private var idleOverlay: View? = null
    private var idleClockText: TextView? = null
    private var idleDateText: TextView? = null
    private var idleTouchStartX = 0f
    private var idleTouchStartY = 0f
    private val idleSwipeThresholdPx: Float by lazy { 28 * resources.displayMetrics.density }
    private val idleFadeDurationMs = 110L

    private val idleClockHandler = Handler(Looper.getMainLooper())
    private val idleClockTicker = object : Runnable {
        override fun run() {
            updateIdleClock()

            val delay = 1000 - (System.currentTimeMillis() % 1000)
            idleClockHandler.postDelayed(this, delay)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ScreenAdapter.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("nihal_home_prefs", MODE_PRIVATE)
        usageStore = UsageStore(this)
        //MobileAds.initialize(this)
        val params = ConsentRequestParameters.Builder().build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { loadAndShowError ->
                    MobileAds.initialize(this)
                }
            },
            {
                MobileAds.initialize(this)
            }
        )

        // Home/launcher windows are laid out edge-to-edge by the system, so
        // android:statusBarColor / navigationBarColor in the theme are ignored
        // on modern Android. Make it explicit and draw our own solid white
        // bars via scrim views instead (set up in setupHomeScreen()).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

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

        if (homeScreenReady && idleOverlay?.visibility != View.VISIBLE) {
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
                            showIdleOverlay()
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
        if (prefs.getBoolean(KEY_SETUP_DONE, false)) {
            showIdleOverlay()
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean(KEY_SETUP_DONE, false)) {
            refreshApps()
            resetUIState()
            updateIdleClock()
            idleClockHandler.removeCallbacks(idleClockTicker)
            idleClockHandler.post(idleClockTicker)
            updateSponsoredSection()
            contentScroll?.post { updateStickySearchBarVisibility() }
        }
    }

    override fun onPause() {
        super.onPause()
        idleClockHandler.removeCallbacks(idleClockTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        idleClockHandler.removeCallbacks(idleClockTicker)
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
            nativeAdManager?.isAdFallbackVisible() == true -> "Sponsored • Error: ${nativeAdManager?.getLastError()}"
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

        // Keep the status bar and navigation bar solid white, never letting
        // the wallpaper show through behind them. Since the window is
        // edge-to-edge, we draw our own opaque bars sized to the system bar
        // insets, and pad the scrollable/idle content so nothing sits under
        // them.
        val statusBarScrim = findViewById<View>(R.id.statusBarScrim)
        val navBarScrim = findViewById<View>(R.id.navBarScrim)
        val rootContainer = findViewById<View>(R.id.rootContainer)
        val contentScrollView = findViewById<NestedScrollView>(R.id.contentScroll)
        val idleOverlayView = findViewById<View>(R.id.homeIdleOverlay)
        val contentScrollInitialPadding = intArrayOf(
            contentScrollView.paddingLeft,
            contentScrollView.paddingTop,
            contentScrollView.paddingRight,
            contentScrollView.paddingBottom
        )
        val idleOverlayInitialPadding = intArrayOf(
            idleOverlayView.paddingLeft,
            idleOverlayView.paddingTop,
            idleOverlayView.paddingRight,
            idleOverlayView.paddingBottom
        )
        val searchBarStickyView = findViewById<View>(R.id.searchBarSticky)
        val searchBarStickyInitialTopMargin =
            (searchBarStickyView.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            statusBarScrim.layoutParams = statusBarScrim.layoutParams.apply { height = bars.top }
            navBarScrim.layoutParams = navBarScrim.layoutParams.apply { height = bars.bottom }
            statusBarScrim.requestLayout()
            navBarScrim.requestLayout()

            contentScrollView.setPadding(
                contentScrollInitialPadding[0] + bars.left,
                contentScrollInitialPadding[1] + bars.top,
                contentScrollInitialPadding[2] + bars.right,
                contentScrollInitialPadding[3] + bars.bottom
            )
            idleOverlayView.setPadding(
                idleOverlayInitialPadding[0] + bars.left,
                idleOverlayInitialPadding[1] + bars.top,
                idleOverlayInitialPadding[2] + bars.right,
                idleOverlayInitialPadding[3] + bars.bottom
            )
            (searchBarStickyView.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin =
                searchBarStickyInitialTopMargin + bars.top
            searchBarStickyView.requestLayout()

            ViewCompat.onApplyWindowInsets(view, insets)
        }
        ViewCompat.requestApplyInsets(rootContainer)

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
        coreAppsAdapter = CoreAppsAdapter(
            onClick = { launchApp(it) },
            onLongClick = { app, view -> showAppContextMenu(app, view) }
        )
        coreAppsRecyclerView.layoutManager = GridLayoutManager(this, 2)
        coreAppsRecyclerView.adapter = coreAppsAdapter

        val sponsoredContainer = findViewById<FrameLayout>(R.id.sponsoredContainer)
        sponsoredContainer.clipToOutline = true
        nativeAdManager = NativeAdManager(applicationContext, sponsoredContainer) {
            updateSponsoredLabel()
        }
        updateSponsoredSection()

        setupSearch()
        refreshApps()
        setupIdleOverlay()
        homeScreenReady = true
    }

    private fun setupIdleOverlay() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

        val overlay = findViewById<View>(R.id.homeIdleOverlay)
        idleOverlay = overlay
        idleClockText = findViewById(R.id.idleClockText)
        idleDateText = findViewById(R.id.idleDateText)

        overlay.alpha = 1f
        overlay.visibility = View.VISIBLE
        contentScroll?.background = null
        contentScroll?.visibility = View.INVISIBLE
        updateIdleClock()

        overlay.setOnTouchListener { _, event -> handleIdleOverlayTouch(event) }

        findViewById<View>(R.id.kotetsuCapsule).setOnClickListener {
            startActivity(Intent(this, KotetsuActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }

    private fun handleIdleOverlayTouch(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                idleTouchStartX = event.x
                idleTouchStartY = event.y
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = event.x - idleTouchStartX
                val dy = event.y - idleTouchStartY
                if (abs(dx) > idleSwipeThresholdPx || abs(dy) > idleSwipeThresholdPx) {
                    revealMainScreen()
                }
            }
        }
        return true
    }

    private fun revealMainScreen() {
        val overlay = idleOverlay ?: return
        if (overlay.visibility != View.VISIBLE) return

        contentScroll?.setBackgroundColor(ContextCompat.getColor(this, R.color.screen_background))
        contentScroll?.visibility = View.VISIBLE

        overlay.animate().cancel()
        overlay.animate()
            .alpha(0f)
            .setDuration(idleFadeDurationMs)
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
            }
            .start()
    }

    private fun showIdleOverlay() {
        val overlay = idleOverlay ?: return
        if (overlay.visibility == View.VISIBLE) return

        resetUIState()
        updateIdleClock()
        contentScroll?.background = null
        contentScroll?.visibility = View.INVISIBLE

        overlay.animate().cancel()
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate()
            .alpha(1f)
            .setDuration(idleFadeDurationMs)
            .start()
    }

    private fun updateIdleClock() {
        val clock = idleClockText ?: return
        val dateView = idleDateText

        val calendar = Calendar.getInstance()

        val timeFormat = SimpleDateFormat("hh:mm", Locale.getDefault())
        val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
        val timeStr = timeFormat.format(calendar.time)
        val amPmStr = amPmFormat.format(calendar.time)
        val fullStr = "$timeStr $amPmStr"

        val spannableTime = android.text.SpannableString(fullStr)
        spannableTime.setSpan(
            android.text.style.RelativeSizeSpan(0.35f),
            timeStr.length,
            fullStr.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        clock.text = spannableTime

        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        dateView?.text = dateFormat.format(calendar.time)
    }

    private fun setupSearch() {
        val searchInput = findViewById<EditText>(R.id.searchInput)
        this.searchInput = searchInput
        val clearIcon = findViewById<View>(R.id.clearIcon)
        this.clearIcon = clearIcon
        val searchIcon = findViewById<View>(R.id.searchIcon)
        this.searchIcon = searchIcon
        val normalContent = findViewById<View>(R.id.normalContent)
        val searchBarContainer = findViewById<ViewGroup>(R.id.searchBarContainer)
        this.searchBarContainer = searchBarContainer

        val searchBarSticky = findViewById<ViewGroup>(R.id.searchBarSticky)
        this.searchBarSticky = searchBarSticky

        // There is only ONE search icon / EditText / clear icon in the whole
        // screen. "searchBarContainer" (inline, inside the scrolling content)
        // and "searchBarSticky" (an empty overlay docked at the top) are just
        // two differently-styled shells. When the user scrolls past the inline
        // bar, the same three child views are physically moved into the sticky
        // shell instead of being duplicated/synced, so there is exactly one
        // cursor, one focus state and one text buffer at all times - no more
        // keeping two EditTexts in sync.
        val clickToFocus = View.OnClickListener {
            searchInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
        searchBarContainer.setOnClickListener(clickToFocus)
        searchBarSticky.setOnClickListener(clickToFocus)

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

        contentScroll?.setOnScrollChangeListener { _, _, _, _, _ ->
            updateStickySearchBarVisibility()
        }
    }

    /**
     * Moves the search icon / EditText / clear icon between the inline shell
     * (searchBarContainer, inside the scrolling content) and the pinned shell
     * (searchBarSticky, an overlay docked to the top) based on scroll
     * position. Because these are the *same* View instances every time
     * (nothing is duplicated or text-synced), focus, cursor position and the
     * IME input connection all move with them automatically - there is no
     * separate "sticky EditText" that can fall out of sync.
     */
    private fun setSearchBarPinned(pinned: Boolean) {
        if (pinned == isSearchBarPinned) return
        val icon = searchIcon ?: return
        val input = searchInput ?: return
        val clear = clearIcon ?: return
        val inlineShell = searchBarContainer ?: return
        val stickyShell = searchBarSticky ?: return

        // Reparenting an EditText clears its focus/IME connection for a
        // moment; remember the state so we can seamlessly restore it and the
        // user never notices (and never has to tap the field again).
        val hadFocus = input.hasFocus()

        val fromShell = if (pinned) inlineShell else stickyShell
        val toShell = if (pinned) stickyShell else inlineShell

        fromShell.removeView(icon)
        fromShell.removeView(input)
        fromShell.removeView(clear)
        toShell.addView(icon)
        toShell.addView(input)
        toShell.addView(clear)

        // Keep the inline shell occupying its normal space (INVISIBLE, not
        // GONE) so the rest of the list doesn't jump when it's emptied out.
        inlineShell.visibility = if (pinned) View.INVISIBLE else View.VISIBLE
        stickyShell.visibility = if (pinned) View.VISIBLE else View.GONE
        isSearchBarPinned = pinned

        if (hadFocus) {
            input.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun updateStickySearchBarVisibility() {
        val bar = searchBarContainer ?: return
        val scroll = contentScroll ?: return

        val barLocation = IntArray(2)
        bar.getLocationOnScreen(barLocation)
        val scrollLocation = IntArray(2)
        scroll.getLocationOnScreen(scrollLocation)
        val visibleTop = scrollLocation[1] + scroll.paddingTop

        val shouldStick = barLocation[1] <= visibleTop
        setSearchBarPinned(shouldStick)
    }

    private fun refreshApps() {
        allApps = AppRepository.loadApps(this)
        applyFilter((findViewById<EditText>(R.id.searchInput)).text?.toString().orEmpty())

        val matchPool = try {
            AppRepository.loadAllInstalledApps(this)
        } catch (e: Exception) {
            allApps
        }

        val roleCandidates = try {
            AppRepository.loadRoleCandidates(this)
        } catch (e: Exception) {
            emptyMap()
        }

        val coreApps = try {
            CoreAppsRepository.detectCoreApps(matchPool, roleCandidates)
        } catch (e: Exception) {
            emptyList()
        }
        updateFrequentApps(coreApps, matchPool)
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

    private fun updateFrequentApps(coreApps: List<AppInfo>, matchPool: List<AppInfo> = allApps) {
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
                FallbackAppsRepository.detectFallbackApps(matchPool, usedPackages)
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
            val matchPool = try {
                AppRepository.loadAllInstalledApps(this)
            } catch (e: Exception) {
                allApps
            }
            val roleCandidates = try {
                AppRepository.loadRoleCandidates(this)
            } catch (e: Exception) {
                emptyMap()
            }
            val coreApps = try {
                CoreAppsRepository.detectCoreApps(matchPool, roleCandidates)
            } catch (e: Exception) {
                emptyList()
            }
            updateFrequentApps(coreApps, matchPool)
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
