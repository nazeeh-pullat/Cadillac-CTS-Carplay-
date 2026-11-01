package com.shihab.diplay.legacy

import android.app.Activity
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.VpnService
import android.net.wifi.WifiManager
import android.provider.Settings
import android.text.InputType
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.Toast
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.LockdownPairRecord
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A small API-22 host for wired and existing-LAN wireless CarPlay. */
class LegacyActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var status: TextView
    private lateinit var video: SurfaceView
    private lateinit var dashboard: View
    private lateinit var returnToCarPlay: Button
    private lateinit var home: View
    private lateinit var settingsPage: View
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: ImageButton
    private lateinit var led: LedStrip
    private var ledSwatches: LinearLayout? = null
    private var lightingPage: FrameLayout? = null
    private var lightingLines: ImageView? = null
    private var lightingTint = LIGHTING_DEFAULT_TINT
    private var lightingOpen = false
    private lateinit var lightingButton: View
    private lateinit var gearButton: View
    private lateinit var actionsRow: View
    private lateinit var idleOverlay: View
    private lateinit var heroView: View
    private lateinit var clockBlock: View
    private lateinit var statusPill: View
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private var retryAttempt = 0
    private var retryPending = false
    private var gaveUp = false
    private var userStopped = false
    private val retryRunnable = Runnable { retryPending = false; updateLink(); autoConnect() }
    private var phoneReceiver: android.content.BroadcastReceiver? = null
    private var idle = false
    private var swallowGesture = false
    private val idleRunnable = Runnable { enterIdle() }
    private var settingsOpen = false
    private var directHotspot = true
    private var carPlayActive = false
    private var controller: CarPlayController? = null
    private var sink: AndroidMediaSink? = null
    private var testTone: android.media.ToneGenerator? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var destroyed = false
    private val diagnosticLock = Any()
    private var wireless = false
    private var selectedPhone: String? = null
    private var wirelessSsid = ""
    private var wirelessPassword = ""
    private var hotspotMode = false
    @Volatile private var tabletHotspot: TabletHotspot? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        led = LedStrip(this, getSharedPreferences("legacy", MODE_PRIVATE))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = SurfaceView(this).apply {
            holder.addCallback(this@LegacyActivity)
            setOnTouchListener { view, event -> forwardTouch(view, event) }
        }
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        dashboard = createDashboard()
        root.addView(dashboard, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        enterImmersiveMode()
        watchPhoneBluetooth()
        if (state == null) main.postDelayed({ autoConnect() }, AUTO_START_DELAY_MS)
        if (state == null) {
            val startup = StartupModes.get(getSharedPreferences("legacy", MODE_PRIVATE))
            val ready = startPreparation()
            if (startup.playsAnimation) showSplash(root, startup, ready)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (!toggleCarPlayControls()) finish()
            }
        }
        val usb = getSystemService(Context.USB_SERVICE) as? UsbManager
        Log.i(TAG, "Ready api=${android.os.Build.VERSION.SDK_INT} usbHost=" +
            packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) + " devices=${usb?.deviceList?.size ?: 0}")
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun panel(color: Int, radius: Int = CARD_RADIUS) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun createDashboard(): View {
        val pages = FrameLayout(this)
        // Not clipping children lets the colour strips grow past their own bounds and cast their shadows.
        home = FrameLayout(this).apply { background = CadillacBackground(); clipChildren = false }
        val gear = iconButton(R.drawable.ic_settings, "Settings") { openSettings() }
        gearButton = gear
        (home as FrameLayout).addView(gear, FrameLayout.LayoutParams(dp(ICON_TARGET), dp(ICON_TARGET), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(PAGE_PADDING); marginEnd = dp(PAGE_PADDING)
        })
        buildClockAndStatus()
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.cadillac_logo_name); contentDescription = "Cadillac"
        }, LinearLayout.LayoutParams(dp(HERO_LOGO_WIDTH), dp(HERO_LOGO_HEIGHT)))
        val lighting = iconButton(R.drawable.ic_lighting, "Lighting") { openLighting() }.apply {
            scaleType = ImageView.ScaleType.CENTER; setPadding(0, 0, 0, 0)
            // Dark glass panel with a fine champagne edge, matching the Connect button's colours.
            val glass = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xff24262b.toInt(), 0xff0f1012.toInt())).apply {
                cornerRadius = dp(CARD_RADIUS).toFloat(); setStroke(dp(1), 0x99e9dcb8.toInt())
            }
            background = RippleDrawable(ColorStateList.valueOf(0x33e9dcb8), glass, null)
            elevation = dp(4).toFloat()
        }
        lightingButton = lighting
        (home as FrameLayout).addView(lighting, FrameLayout.LayoutParams(dp(LIGHTING_BUTTON), dp(LIGHTING_BUTTON), Gravity.TOP or Gravity.START).apply {
            topMargin = dp(PAGE_PADDING); marginStart = dp(PAGE_PADDING)
        })
        connectButton = dashboardButton("Connect to iPhone", true) {
            if (carPlayActive) showDashboard(false) else prepareWireless(true)
        }.apply {
            textSize = 22f; minimumHeight = dp(CONNECT_HEIGHT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            contentDescription = "Connect to iPhone"; text = connectLabel(false)
            val champagne = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xfff3efe4.toInt(), 0xffd9cdb0.toInt())).apply {
                cornerRadius = dp(CARD_RADIUS).toFloat(); setStroke(dp(1), 0xffe9dcb8.toInt())
            }
            background = RippleDrawable(ColorStateList.valueOf(0x33806b3c), champagne, null)
        }
        disconnectButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_power); contentDescription = "Disconnect"
            scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(30), dp(30), dp(30), dp(30))
            val red = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xffd72a45.toInt(), 0xffa80d27.toInt())).apply {
                cornerRadius = dp(CARD_RADIUS).toFloat(); setStroke(dp(1), 0xffe5566b.toInt())
            }
            background = RippleDrawable(ColorStateList.valueOf(0x44ffffff), red, null)
            visibility = View.GONE
            setOnClickListener { userStop(); showStatus("Disconnected. Ready when you are.") }
        }
        val gap = dp(BLOCK_GAP)
        val buttonWidth = minOf(CONNECT_WIDTH, resources.configuration.screenWidthDp - PAGE_PADDING * 2 - CONNECT_HEIGHT - BLOCK_GAP)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actionsRow = actions
        actions.addView(connectButton, LinearLayout.LayoutParams(dp(buttonWidth), -2))
        actions.addView(disconnectButton, LinearLayout.LayoutParams(dp(CONNECT_HEIGHT), dp(CONNECT_HEIGHT)).apply { marginStart = gap })
        hero.addView(actions, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(HERO_ACTION_GAP) })
        (home as FrameLayout).addView(hero, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        (home as FrameLayout).addView(CadillacLineView(this), FrameLayout.LayoutParams(-1, dp(LINE_HEIGHT), Gravity.BOTTOM).apply { bottomMargin = dp(LINE_MARGIN) })
        // Dims everything under the logo while Home is idle; the logo is brought back in front of it.
        idleOverlay = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0f; visibility = View.GONE }
        (home as FrameLayout).addView(idleOverlay, FrameLayout.LayoutParams(-1, -1))
        heroView = hero
        hero.bringToFront()
        if (LedModes.address(getSharedPreferences("legacy", MODE_PRIVATE)) != null) led.connect()
        settingsPage = createSettingsContent().apply { visibility = View.GONE }
        pages.addView(home, FrameLayout.LayoutParams(-1, -1))
        pages.addView(settingsPage, FrameLayout.LayoutParams(-1, -1))
        return pages
    }

    private fun iconButton(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon); contentDescription = description
        background = RippleDrawable(ColorStateList.valueOf(0x22e9e7e2), panel(CARD).apply { setStroke(dp(1), HAIRLINE) }, null)
        setPadding(dp(CARD_PADDING), dp(CARD_PADDING), dp(CARD_PADDING), dp(CARD_PADDING))
        minimumHeight = dp(ICON_TARGET); minimumWidth = dp(ICON_TARGET)
        setOnClickListener { action() }
    }

    private fun scheduleIdle() {
        main.removeCallbacks(idleRunnable)
        val seconds = ScreenTimeouts.get(getSharedPreferences("legacy", MODE_PRIVATE)).seconds
        if (!destroyed && seconds > 0) main.postDelayed(idleRunnable, seconds * 1000L)
    }

    /**
     * Home dims to the Cadillac logo alone. The logo grows and glides to the middle while Connect, Settings, the strips and
     * the line fade into the dark; everything is a short property animation, so it stays light on this tablet.
     */
    private fun enterIdle() {
        if (idle || destroyed || settingsOpen || dashboard.visibility != View.VISIBLE || home.visibility != View.VISIBLE) return
        idle = true
        idleOverlay.animate().cancel(); idleOverlay.visibility = View.VISIBLE
        idleOverlay.animate().alpha(IDLE_DIM_ALPHA).setDuration(IDLE_FADE_MS).setInterpolator(android.view.animation.DecelerateInterpolator(1.4f)).start()
        // The logo sits above the Connect row; move it down by half that row so it ends up centred once the row is gone.
        val rowShift = (actionsRow.height + dp(HERO_ACTION_GAP)) / 2f * IDLE_LOGO_SCALE
        heroView.animate().cancel()
        heroView.animate().scaleX(IDLE_LOGO_SCALE).scaleY(IDLE_LOGO_SCALE).translationY(rowShift).setDuration(IDLE_FADE_MS)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f)).withLayer().start()
        for (view in listOf(gearButton, lightingButton, actionsRow, clockBlock, statusPill)) {
            view.animate().cancel()
            view.animate().alpha(0f).setDuration(IDLE_FADE_MS).withEndAction { if (idle) view.visibility = View.INVISIBLE }.start()
        }
    }

    private fun wakeFromIdle() {
        idle = false
        idleOverlay.animate().cancel()
        idleOverlay.animate().alpha(0f).setDuration(WAKE_FADE_MS).withEndAction { if (!idle) idleOverlay.visibility = View.GONE }.start()
        heroView.animate().cancel()
        heroView.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(WAKE_FADE_MS)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f)).withLayer().start()
        for (view in listOf(gearButton, lightingButton, actionsRow, clockBlock, statusPill)) {
            view.animate().cancel(); view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(WAKE_FADE_MS).start()
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && idle) { wakeFromIdle(); swallowGesture = true }
        if (dashboard.visibility == View.VISIBLE) scheduleIdle()
        if (swallowGesture) {
            // The touch that wakes Home is not also a tap on whatever sits under the finger.
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) swallowGesture = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        scheduleIdle()
    }

    override fun onPause() {
        main.removeCallbacks(idleRunnable)
        super.onPause()
    }

    internal fun openSettings() {
        main.removeCallbacks(idleRunnable)
        settingsOpen = true
        home.visibility = View.GONE
        slideIn(settingsPage, true)
    }

    /**
     * Pages swap with the old one gone at once and the new one easing in: a short fade plus a small slide on a hardware
     * layer, so the weak GPU only moves one flat texture. Going forward slides in from the right, going Home from the left.
     */
    private fun slideIn(page: View, fromRight: Boolean) {
        page.animate().cancel()
        page.visibility = View.VISIBLE
        page.alpha = 0f
        page.translationX = dp(PAGE_SLIDE) * (if (fromRight) 1 else -1).toFloat()
        page.animate().alpha(1f).translationX(0f).setDuration(PAGE_IN_MS)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f)).withLayer().start()
    }

    /** The lighting page: the car-interior line art, tinted with the colour picked from the dots along the bottom. */
    private fun openLighting() {
        main.removeCallbacks(idleRunnable)
        val page = lightingPage ?: buildLightingPage().also { lightingPage = it; (dashboard as FrameLayout).addView(it, FrameLayout.LayoutParams(-1, -1)) }
        lightingOpen = true
        home.visibility = View.GONE
        refreshLedSwatches()
        slideIn(page, true)
    }

    private fun buildLightingPage(): FrameLayout {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val selected = LedModes.colorIndex(prefs)
        lightingTint = if (selected >= 0) lightingTintFor(LedModes.palette[selected]) else LIGHTING_DEFAULT_TINT
        val lines = ImageView(this).apply {
            setImageResource(R.drawable.lighting_lines); scaleType = ImageView.ScaleType.CENTER_CROP
            setColorFilter(lightingTint, PorterDuff.Mode.SRC_IN); contentDescription = "Lighting preview"
        }
        lightingLines = lines
        val swatches = buildLedSwatches().also { ledSwatches = it }
        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK); isClickable = true; visibility = View.GONE
            addView(lines, FrameLayout.LayoutParams(-1, -1))
            addView(swatches, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(LED_SWATCH_MARGIN) })
            addView(iconButton(R.drawable.ic_home, "Home") { showHome() }, FrameLayout.LayoutParams(dp(LIGHTING_BUTTON), dp(LIGHTING_BUTTON), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(PAGE_PADDING); marginStart = dp(PAGE_PADDING) // exactly where the Lighting button sits on Home
            })
        }
    }

    /** The lines take the strip's colour; "off" leaves them a dim grey rather than black. */
    private fun lightingTintFor(color: LedColor) = if (color.name == "off") LIGHTING_OFF_TINT else color.argb

    private fun tintLighting(color: LedColor) {
        val lines = lightingLines ?: return
        val to = lightingTintFor(color)
        val from = lightingTint
        lightingTint = to
        android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), from, to).apply {
            duration = LIGHTING_FADE_MS
            addUpdateListener { lines.setColorFilter(it.animatedValue as Int, PorterDuff.Mode.SRC_IN) }
        }.start()
    }

    private fun showHome() {
        settingsOpen = false
        lightingOpen = false
        lightingPage?.let { it.animate().cancel(); it.visibility = View.GONE }
        settingsPage.animate().cancel(); settingsPage.visibility = View.GONE
        slideIn(home, false)
        scheduleIdle()
    }

    /** Prepares authentication off the main thread; returns a flag that turns true when it is done. */
    private fun startPreparation(): java.util.concurrent.atomic.AtomicBoolean {
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        Thread({
            try { installAuthentication(); if (!destroyed) loadIdentity() }
            catch (error: Throwable) { diagnostic("Startup preparation: ${error.javaClass.simpleName}") }
            finally { ready.set(true) }
        }, "DiPlay-startup").start()
        return ready
    }

    private fun showSplash(root: FrameLayout, startup: StartupAnimation, ready: java.util.concurrent.atomic.AtomicBoolean) {
        val animation = SplashAnimationView(this)
        val splash = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK); isClickable = true
            addView(animation, FrameLayout.LayoutParams(-1, -1))
            addView(CadillacLineView(this@LegacyActivity), FrameLayout.LayoutParams(-1, dp(LINE_HEIGHT), Gravity.BOTTOM).apply { bottomMargin = dp(LINE_MARGIN) })
        }
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        animation.start(startup.speed)
        val started = android.os.SystemClock.elapsedRealtime()
        val hold = if (startup == StartupAnimation.FULL) SPLASH_HOLD_MS else SPLASH_SHORT_HOLD_MS
        var finishedAt = 0L
        fun finishWhenReady() {
            if (destroyed) return
            val now = android.os.SystemClock.elapsedRealtime()
            if (finishedAt == 0L && animation.finished) finishedAt = now
            val held = finishedAt != 0L && now - finishedAt >= hold
            if ((held && ready.get()) || now - started >= SPLASH_MAX_MS) {
                splash.animate().alpha(0f).setDuration(if (startup == StartupAnimation.FULL) 400L else 250L).withEndAction { root.removeView(splash) }.start()
            } else main.postDelayed({ finishWhenReady() }, 50)
        }
        main.postDelayed({ finishWhenReady() }, 100)
    }
    private fun rowCopy(title: String, detail: String): CharSequence = android.text.SpannableString("$title\n$detail").apply {
        setSpan(android.text.style.RelativeSizeSpan(0.78f), title.length + 1, length, 0)
        setSpan(android.text.style.ForegroundColorSpan(MUTED), title.length + 1, length, 0)
    }

    private fun startupDetail(prefs: android.content.SharedPreferences): String =
        "${StartupModes.get(prefs).label} · applies the next time the app opens"

    private fun ledDetail(prefs: android.content.SharedPreferences): String {
        val name = LedModes.name(prefs) ?: return "Tap to scan for nearby Bluetooth LED controllers"
        return "$name · ${led.summary}"
    }

    private fun swatchBackground(color: LedColor, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (color.name == "off") 0xff101214.toInt() else color.argb)
        setStroke(dp(if (selected) 3 else 1), if (selected) Color.WHITE else 0x55ffffff)
    }

    /** Colours only, no text: a quiet row of dots along the bottom of Home, shown once a strip is saved. */
    private fun buildLedSwatches(): LinearLayout {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val size = minOf(40, ((resources.configuration.screenWidthDp - PAGE_PADDING * 2) / (LedModes.palette.size * 1.35f)).toInt())
        val gap = dp(maxOf(6, size / 4))
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            LedModes.palette.forEachIndexed { index, color ->
                addView(View(this@LegacyActivity).apply {
                    contentDescription = "LED ${color.name}"; isClickable = true
                    background = swatchBackground(color, index == LedModes.colorIndex(prefs))
                    setOnClickListener { pickLed(index) }
                }, LinearLayout.LayoutParams(dp(size), dp(size)).apply { marginStart = gap / 2; marginEnd = gap / 2 })
            }
        }
    }

    private fun refreshLedSwatches() {
        val swatches = ledSwatches ?: return
        val selected = LedModes.colorIndex(getSharedPreferences("legacy", MODE_PRIVATE))
        LedModes.palette.forEachIndexed { index, color -> swatches.getChildAt(index).background = swatchBackground(color, index == selected) }
    }

    private fun pickLed(index: Int) {
        val color = LedModes.palette[index]
        LedModes.saveColor(getSharedPreferences("legacy", MODE_PRIVATE), index)
        led.setColor(color.red, color.green, color.blue)
        refreshLedSwatches()
        tintLighting(color)
    }

    private fun testLed() {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        if (LedModes.address(prefs) == null) { Toast.makeText(this, "Find your LED strip first.", Toast.LENGTH_LONG).show(); return }
        val steps = listOf(LedModes.named("red"), LedModes.named("green"), LedModes.named("blue"))
        steps.forEachIndexed { i, c -> main.postDelayed({ if (!destroyed) led.setColor(c.red, c.green, c.blue) }, i * 900L) }
        main.postDelayed({
            if (destroyed) return@postDelayed
            val restore = LedModes.colorIndex(prefs).takeIf { it >= 0 }?.let { LedModes.palette[it] } ?: LedModes.palette.last()
            led.setColor(restore.red, restore.green, restore.blue)
        }, steps.size * 900L)
        Toast.makeText(this, "Watch the strip: red, green, blue.", Toast.LENGTH_SHORT).show()
    }

    private fun chooseLedProtocol(changed: () -> Unit) {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val modes = LedProtocol.values()
        AlertDialog.Builder(this).setTitle("Controller type")
            .setSingleChoiceItems(modes.map { it.label }.toTypedArray<CharSequence>(), modes.indexOf(LedModes.protocol(prefs))) { dialog, which ->
                LedModes.saveProtocol(prefs, modes[which]); led.close(); led.connect(); changed(); dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseStartupAnimation(changed: () -> Unit) {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val modes = StartupAnimation.values()
        val items = modes.map { "${it.label}\n${it.note}" }.toTypedArray<CharSequence>()
        AlertDialog.Builder(this).setTitle("Startup animation")
            .setSingleChoiceItems(items, modes.indexOf(StartupModes.get(prefs))) { dialog, which ->
                StartupModes.save(prefs, modes[which]); changed(); dialog.dismiss()
                Toast.makeText(this, "Saved. It applies the next time the app opens.", Toast.LENGTH_LONG).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun resolutionDetail(prefs: android.content.SharedPreferences): String {
        val mode = VideoModes.resolution(prefs)
        return "${mode.label} · ${mode.name} · applies the next time you connect"
    }

    private fun frameRateDetail(prefs: android.content.SharedPreferences) =
        "${VideoModes.frameRateLabel(VideoModes.fps(prefs))} · applies the next time you connect"

    /** The video size is fixed when CarPlay connects, so a change needs a fresh start of the app. */
    private fun offerRestart(summary: String) {
        AlertDialog.Builder(this).setTitle("Restart to apply?")
            .setMessage("$summary Restart the app now so the next connection uses it. CarPlay will disconnect.")
            .setPositiveButton("Restart app") { _, _ -> restartApp() }
            .setNegativeButton("Later", null).show()
    }

    private fun restartApp() {
        stopConnection()
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
        val pending = android.app.PendingIntent.getActivity(this, 0, launch, android.app.PendingIntent.FLAG_CANCEL_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 500, pending)
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun chooseResolution(changed: () -> Unit) {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val current = VideoModes.resolution(prefs)
        val items = VideoModes.resolutions.map { "${it.label} · ${it.name}\n${it.note}" }.toTypedArray<CharSequence>()
        AlertDialog.Builder(this).setTitle("Resolution")
            .setSingleChoiceItems(items, VideoModes.resolutions.indexOf(current)) { dialog, which ->
                VideoModes.saveResolution(prefs, VideoModes.resolutions[which])
                changed(); dialog.dismiss()
                offerRestart("Resolution set to ${VideoModes.resolutions[which].label}.")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseFrameRate(changed: () -> Unit) {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val items = VideoModes.frameRates.map { VideoModes.frameRateLabel(it) }.toTypedArray<CharSequence>()
        AlertDialog.Builder(this).setTitle("Frame rate")
            .setSingleChoiceItems(items, VideoModes.frameRates.indexOf(VideoModes.fps(prefs))) { dialog, which ->
                VideoModes.saveFps(prefs, VideoModes.frameRates[which])
                changed(); dialog.dismiss()
                offerRestart("Frame rate set to ${VideoModes.frameRateLabel(VideoModes.frameRates[which])}.")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun createSettingsContent(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(BACKGROUND); isVerticalScrollBarEnabled = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(PAGE_PADDING), dp(PAGE_PADDING), dp(PAGE_PADDING), dp(PAGE_PADDING))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val width = minOf(resources.configuration.screenWidthDp - PAGE_PADDING * 2, CONTENT_WIDTH).coerceAtLeast(0)
        body.addView(content, LinearLayout.LayoutParams(dp(width), -2))
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        fun label(text: String, size: Float, color: Int) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color) }
        fun section(title: String) {
            content.addView(label(title, 15f, ACCENT), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(SECTION_GAP); bottomMargin = dp(BLOCK_GAP)
            })
        }
        fun row(title: String, detail: String, action: () -> Unit): Button {
            val button = dashboardButton(title, false, action = action).apply {
                text = rowCopy(title, detail); textSize = 18f; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minimumHeight = dp(SETTINGS_ROW_HEIGHT)
                setPadding(dp(CARD_PADDING), dp(CARD_PADDING), dp(CARD_PADDING), dp(CARD_PADDING))
                setLineSpacing(dp(TEXT_LINE_GAP).toFloat(), 1f)
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0)
                compoundDrawablePadding = dp(CARD_PADDING)
            }
            content.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ROW_GAP) })
            return button
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.cadillac_logo); contentDescription = "Cadillac crest" },
            LinearLayout.LayoutParams(dp(CREST_WIDTH), dp(CREST_HEIGHT)))
        header.addView(label("Settings", 28f, FOREGROUND), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(CARD_PADDING) })
        header.addView(iconButton(R.drawable.ic_home, "Home") { showHome() }, LinearLayout.LayoutParams(dp(ICON_TARGET), dp(ICON_TARGET)))
        content.addView(header)
        returnToCarPlay = dashboardButton("Return to CarPlay", true) { showHome(); showDashboard(false) }.apply { visibility = View.GONE }
        content.addView(returnToCarPlay, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(BLOCK_GAP) })
        section("Connection status")
        status = label("Ready to connect\nDefault: tablet hotspot + Bluetooth", 15f, FOREGROUND).apply {
            background = panel(CARD).apply { setStroke(dp(1), HAIRLINE) }
            setPadding(dp(CARD_PADDING), dp(CARD_PADDING), dp(CARD_PADDING), dp(CARD_PADDING))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(status)
        section("Connection")
        row("Reconnect iPhone", "Restart the tablet hotspot and reconnect now") { prepareWireless(true) }
        val autoRow = row("Auto-connect", autoConnectDetail()) { }
        autoRow.setOnClickListener {
            val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
            AutoConnect.save(prefs, !AutoConnect.enabled(prefs))
            if (AutoConnect.enabled(prefs)) { userStopped = false; autoConnect() } else cancelRetry()
            autoRow.text = rowCopy("Auto-connect", autoConnectDetail())
        }
        row("Pair iPhone", "Open Bluetooth settings to pair a new phone") { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        row("Choose iPhone", "Select the paired phone to use next time") { choosePreferredPhone() }
        row("Hotspot password", "$HOTSPOT_SSID · change the password for the next connection") { editHotspotPassword() }
        row("Other wireless modes", "Use an existing Wi-Fi network or configure the hotspot") { prepareWireless(false) }
        row("Start wired", "Connect an iPhone using a USB OTG adapter") { stopConnection(); wireless = false; prepareConnection() }
        row("Stop", "Disconnect CarPlay and turn off the app's hotspot") { userStop(); showStatus("Stopped. Ready when you are.") }
        section("LED strip")
        val ledPrefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val ledRow = row("LED strip", ledDetail(ledPrefs)) { }
        ledRow.setOnClickListener {
            LedScannerDialog(this, ledPrefs) { led.close(); led.connect(); refreshLedSwatches(); ledRow.text = rowCopy("LED strip", ledDetail(ledPrefs)) }.show()
        }
        led.onChange = { ledRow.text = rowCopy("LED strip", ledDetail(ledPrefs)) }
        fun rangeDetail() = "${LedModes.scanRange(ledPrefs).label} · hold the tablet near the strip and pick Close to find it"
        val rangeRow = row("Scan signal range", rangeDetail()) { }
        rangeRow.setOnClickListener {
            val ranges = LedScanRange.values()
            AlertDialog.Builder(this).setTitle("Scan signal range")
                .setSingleChoiceItems(ranges.map { "${it.label}\n${it.note}" }.toTypedArray<CharSequence>(), ranges.indexOf(LedModes.scanRange(ledPrefs))) { dialog, which ->
                    LedModes.saveScanRange(ledPrefs, ranges[which]); rangeRow.text = rowCopy("Scan signal range", rangeDetail()); dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
        }
        row("Test colours", "Cycle red, green and blue on the saved strip") { testLed() }
        val protocolRow = row("Controller type", "${LedModes.protocol(ledPrefs).label} · change if the colours do not match") { }
        protocolRow.setOnClickListener { chooseLedProtocol { protocolRow.text = rowCopy("Controller type", "${LedModes.protocol(ledPrefs).label} · change if the colours do not match") } }
        row("Forget LED strip", "Remove the saved strip and stop sending colours to it") {
            LedModes.forget(ledPrefs); led.close(); refreshLedSwatches(); ledRow.text = rowCopy("LED strip", ledDetail(ledPrefs))
        }
        section("Startup")
        val startupPrefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val startupRow = row("Startup animation", startupDetail(startupPrefs)) { }
        startupRow.setOnClickListener { chooseStartupAnimation { startupRow.text = rowCopy("Startup animation", startupDetail(startupPrefs)) } }
        fun timeoutDetail() = "${ScreenTimeouts.get(startupPrefs).label} · Home dims to the Cadillac logo until touched"
        val timeoutRow = row("Screen timeout", timeoutDetail()) { }
        timeoutRow.setOnClickListener {
            val choices = ScreenTimeout.values()
            AlertDialog.Builder(this).setTitle("Screen timeout")
                .setSingleChoiceItems(choices.map { it.label }.toTypedArray<CharSequence>(), choices.indexOf(ScreenTimeouts.get(startupPrefs))) { dialog, which ->
                    ScreenTimeouts.save(startupPrefs, choices[which]); timeoutRow.text = rowCopy("Screen timeout", timeoutDetail()); dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
        }
        section("Video")
        val videoPrefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val resolutionRow = row("Resolution", resolutionDetail(videoPrefs)) { }
        resolutionRow.setOnClickListener { chooseResolution { resolutionRow.text = rowCopy("Resolution", resolutionDetail(videoPrefs)) } }
        val rateRow = row("Frame rate", frameRateDetail(videoPrefs)) { }
        rateRow.setOnClickListener { chooseFrameRate { rateRow.text = rowCopy("Frame rate", frameRateDetail(videoPrefs)) } }
        section("Display & device")
        row("Display settings", "Adjust tablet brightness and screen settings") { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        row("Wi-Fi settings", "Inspect or change the tablet's Wi-Fi connection") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        row("Tablet hotspot settings", "Inspect the Android hotspot configuration") { startActivity(Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")) }
        section("Diagnostics")
        row("Run connection checks", "Check Bluetooth, pairing, Wi-Fi, hotspot and USB support") { showReadinessChecks() }
        row("USB check", "See which USB devices Android can detect") { showResult("USB check", usbSummary()) }
        row("Test sound", "Play a short tone through the tablet's media output") { testSound() }
        row("App information", "Tablet, app version, CarPlay format and feature support") { showAppInformation() }
        content.addView(label("CarPlay hides all controls and Android bars. Swipe from an edge and press Android Back to return here.", 13f, MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(BLOCK_GAP) })
        return scroll
    }

    private fun dashboardButton(label: String, primary: Boolean, quiet: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f
        setTextColor(if (primary) BACKGROUND else FOREGROUND)
        val surface = panel(if (primary) FOREGROUND else if (quiet) Color.TRANSPARENT else CARD, BUTTON_HEIGHT / 7)
        if (!primary) surface.setStroke(dp(1), HAIRLINE)
        background = RippleDrawable(ColorStateList.valueOf(0x22e9e7e2), surface, null)
        minimumHeight = dp(BUTTON_HEIGHT)
        minWidth = 0
        setPadding(dp(BLOCK_GAP), dp(BLOCK_GAP), dp(BLOCK_GAP), dp(BLOCK_GAP))
        setOnClickListener { action() }
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    internal fun setCarPlayActive(active: Boolean) {
        val update = {
            if (!destroyed) {
                carPlayActive = active
                if (active) { main.removeCallbacks(retryRunnable); retryPending = false; retryAttempt = 0; gaveUp = false }
                setConnecting(false)
                returnToCarPlay.visibility = if (active) View.VISIBLE else View.GONE
                if (active) showHome()
                showDashboard(!active)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update() else main.post { update() }
    }

    private fun showDashboard(show: Boolean) {
        dashboard.visibility = if (show) View.VISIBLE else View.GONE
        if (show) scheduleIdle() else main.removeCallbacks(idleRunnable)
        enterImmersiveMode()
    }

    @Deprecated("Legacy Android back callback")
    @SuppressLint("GestureBackNavigation") // API 33+ uses the registered platform callback above; API 22 needs this one.
    override fun onBackPressed() {
        if (!toggleCarPlayControls()) super.onBackPressed()
    }

    private fun toggleCarPlayControls(): Boolean {
        if (settingsOpen || lightingOpen) { showHome(); return true }
        if (!carPlayActive) return false
        val reveal = dashboard.visibility != View.VISIBLE
        if (reveal) openSettings()
        showDashboard(reveal)
        return true
    }

    private fun prepareConnection() {
        if (controller != null) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            showStatus("This firmware does not advertise USB host support. Wired CarPlay needs a working USB OTG host connection.")
            return
        }
        val permission = VpnService.prepare(this)
        if (permission != null) startActivityForResult(permission, VPN_REQUEST)
        else startConnection()
    }

    @Suppress("DEPRECATION")
    private fun prepareWireless(useTabletHotspot: Boolean = directHotspot) {
        directHotspot = useTabletHotspot
        userStopped = false
        cancelRetry()
        stopConnection()
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_PERMISSION)
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { showStatus("Wireless CarPlay needs Bluetooth, which is unavailable on this tablet."); return }
        if (!adapter.isEnabled) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), BLUETOOTH_REQUEST)
            return
        }
        val phones = adapter.bondedDevices.orEmpty().sortedBy { it.name.orEmpty() }
        if (phones.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Pair your iPhone")
                .setMessage("Pair the tablet and iPhone in Bluetooth settings, then return and tap Connect to iPhone.")
                .setPositiveButton("Bluetooth settings") { _, _ -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        val saved = getSharedPreferences("legacy", MODE_PRIVATE).getString("phone", null)
        val preferred = phones.firstOrNull { it.address == saved }
        if (directHotspot && preferred != null) configureWireless(preferred)
        else if (phones.size == 1) configureWireless(phones.single())
        else AlertDialog.Builder(this).setTitle("Choose your paired iPhone")
            .setItems(phones.map { it.name ?: "Paired device" }.toTypedArray()) { _, index -> configureWireless(phones[index]) }
            .setNegativeButton("Cancel", null).show()
    }

    @Deprecated("Platform permission callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) prepareWireless()
            else showStatus("Bluetooth permission is needed for wireless CarPlay.")
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWireless(phone: BluetoothDevice) {
        if (directHotspot) { hotspotMode = true; beginTabletConnection(phone, hotspotPassword()); return }
        AlertDialog.Builder(this).setTitle("Wireless connection")
            .setItems(arrayOf("Tablet hotspot · no router needed", "Existing Wi-Fi · same network")) { _, index ->
                hotspotMode = index == 0
                if (hotspotMode) configureTabletHotspot(phone) else configureExistingWifi(phone)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun configureTabletHotspot(phone: BluetoothDevice) {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val network = HOTSPOT_SSID
        val secret = prefs.getString("hotspot_password", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        fields.addView(TextView(this).apply {
            text = "Creates Wi-Fi directly from this tablet. No SIM or internet is needed on the tablet. This disconnects its current Wi-Fi until you tap Stop.\n\nNetwork: $network"
        })
        val password = EditText(this).apply {
            hint = "Hotspot password (8–63 characters)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(); setText(secret)
        }
        fields.addView(password)
        val dialog = AlertDialog.Builder(this).setTitle("Tablet hotspot (experimental)")
            .setView(fields).setPositiveButton("Start hotspot", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = password.text.toString()
                if (value.length !in 8..63 || value.any { it.code !in 32..126 }) {
                    password.error = "Use 8–63 English letters, numbers or symbols"; return@setOnClickListener
                }
                dialog.dismiss()
                beginTabletConnection(phone, value)
            }
        }
        dialog.show()
    }

    private fun hotspotPassword(): String {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        return prefs.getString("hotspot_password", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            .also { prefs.edit().putString("hotspot_password", it).apply() }
    }

    private fun editHotspotPassword() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(); setText(hotspotPassword())
        }
        val dialog = AlertDialog.Builder(this).setTitle("Hotspot password")
            .setMessage("Network: $HOTSPOT_SSID. Changes apply the next time you connect.")
            .setView(input).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString()
            if (value.length !in 8..63 || value.any { it.code !in 32..126 }) input.error = "Use 8–63 English letters, numbers or symbols"
            else { getSharedPreferences("legacy", MODE_PRIVATE).edit().putString("hotspot_password", value).apply(); dialog.dismiss() }
        } }
        dialog.show()
    }

    private fun beginTabletConnection(phone: BluetoothDevice, secret: String) {
        val ap = TabletHotspot(this)
        tabletHotspot = ap
        hotspotMode = true; wireless = true; wirelessSsid = HOTSPOT_SSID; wirelessPassword = secret; selectedPhone = phone.address
        getSharedPreferences("legacy", MODE_PRIVATE).edit().putString("hotspot_password", secret).putString("phone", phone.address).apply()
        setConnecting(true)
        showStatus("Restarting tablet hotspot. Accept the CarPlay connection on your iPhone.")
        Thread({
            try {
                ap.start(wirelessSsid, secret)
                main.post { if (!destroyed && tabletHotspot === ap) awaitTabletHotspot(ap, android.os.SystemClock.elapsedRealtime() + 20_000) }
            } catch (error: Exception) {
                main.post { if (!destroyed && tabletHotspot === ap) {
                    stopConnection()
                    connectionFailure("Hotspot could not start: ${error.cause?.message ?: error.message}")
                } }
            }
        }, "DiPlay-hotspot").start()
    }

    private var connectingNow = false
    private val connectSpinner by lazy { SpinnerDrawable(BACKGROUND, dp(3).toFloat()) }
    private val spinTick = object : Runnable {
        override fun run() { connectButton.invalidate(); if (connectingNow && !destroyed) connectButton.postOnAnimation(this) }
    }

    private fun connectLabel(connecting: Boolean) = ConnectLabel.build(
        if (connecting) "Connecting" else if (carPlayActive) "Return to" else "Connect",
        if (connecting) connectSpinner else resources.getDrawable(R.drawable.ic_apple, theme), dp(28), dp(10))

    private fun setConnecting(connecting: Boolean) {
        connectingNow = connecting
        connectButton.isEnabled = !connecting
        connectButton.alpha = if (connecting) 0.92f else 1f
        connectButton.text = connectLabel(connecting)
        connectButton.contentDescription = if (carPlayActive && !connecting) "Return to iPhone" else "Connect to iPhone"
        disconnectButton.visibility = if (carPlayActive || connecting) View.VISIBLE else View.GONE
        if (connecting) connectButton.postOnAnimation(spinTick)
        updateLink()
    }

    private fun connectionFailure(message: String) {
        showStatus(message)
        setConnecting(false)
        scheduleRetry()
        if (!retryPending) Toast.makeText(this, "Could not connect. Open Settings for details.", Toast.LENGTH_LONG).show()
    }

    private fun autoConnectDetail() =
        if (AutoConnect.enabled(getSharedPreferences("legacy", MODE_PRIVATE)))
            "On · connects when the app opens and when your iPhone's Bluetooth appears, and retries if the link drops"
        else "Off · tap Connect yourself"

    private fun userStop() {
        userStopped = true
        cancelRetry()
        stopConnection()
    }

    private fun cancelRetry() {
        main.removeCallbacks(retryRunnable)
        retryPending = false; retryAttempt = 0; gaveUp = false
        updateLink()
    }

    /** Tries again after a growing pause, a few times; the iPhone's Bluetooth reconnecting later starts it afresh. */
    private fun scheduleRetry() {
        main.removeCallbacks(retryRunnable)
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        if (destroyed || userStopped || !AutoConnect.enabled(prefs) || prefs.getString("phone", null) == null) {
            retryPending = false; updateLink(); return
        }
        if (retryAttempt >= AutoConnect.MAX_ATTEMPTS) {
            retryPending = false; gaveUp = true
            showStatus("Auto-connect paused after ${AutoConnect.MAX_ATTEMPTS} tries. Tap Connect, or it resumes when your iPhone's Bluetooth connects.")
            updateLink(); return
        }
        val delay = AutoConnect.retryDelayMs(retryAttempt)
        retryAttempt++; retryPending = true; gaveUp = false
        showStatus("Trying again in ${delay / 1000} s (attempt $retryAttempt of ${AutoConnect.MAX_ATTEMPTS})")
        updateLink()
        main.postDelayed(retryRunnable, delay)
    }

    /** Starts the saved iPhone's connection without any dialogs; does nothing when it cannot do so quietly. */
    @SuppressLint("MissingPermission")
    private fun autoConnect() {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        if (destroyed || userStopped || !AutoConnect.enabled(prefs) || carPlayActive || connectingNow) return
        val saved = prefs.getString("phone", null) ?: return
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) { adapter.enable(); scheduleRetry(); return }
            val phone = adapter.bondedDevices.orEmpty().firstOrNull { it.address == saved } ?: return
            stopConnection()
            directHotspot = true
            configureWireless(phone)
        } catch (_: SecurityException) { /* needs a Bluetooth permission the user has not granted; Connect asks for it */ }
    }

    /** The saved iPhone's Bluetooth link coming up means the car is on and it is time to connect. */
    @Suppress("DEPRECATION")
    private fun watchPhoneBluetooth() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (device.address != getSharedPreferences("legacy", MODE_PRIVATE).getString("phone", null)) return
                userStopped = false; retryAttempt = 0; gaveUp = false
                autoConnect()
            }
        }
        phoneReceiver = receiver
        registerReceiver(receiver, android.content.IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED))
    }

    private fun updateLink() {
        if (!::statusLabel.isInitialized) return
        val state = LinkState.of(carPlayActive, connectingNow, retryPending, gaveUp)
        statusLabel.text = if (state == LinkState.RETRYING) "Reconnecting $retryAttempt/${AutoConnect.MAX_ATTEMPTS}" else state.label
        (statusDot.background as GradientDrawable).setColor(state.dot)
    }

    /** A thin champagne clock at the top and a small link indicator above the line; both go quiet in dim mode. */
    private fun buildClockAndStatus() {
        val soft = 0x99e9dcb8.toInt()
        fun clock(format12: String, format24: String, size: Float, color: Int, face: String, spacing: Float) = android.widget.TextClock(this).apply {
            this.format12Hour = format12; this.format24Hour = format24
            textSize = size; setTextColor(color); typeface = Typeface.create(face, Typeface.NORMAL); letterSpacing = spacing
        }
        val time = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
        time.addView(clock("h:mm", "HH:mm", 38f, 0xffe9dcb8.toInt(), "sans-serif-thin", 0.06f))
        val date = clock("EEE d MMM", "EEE d MMM", 11f, soft, "sans-serif-medium", 0.28f).apply { isAllCaps = true; gravity = Gravity.CENTER }
        clockBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(time); addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = -dp(2) })
        }
        (home as FrameLayout).addView(clockBlock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(PAGE_PADDING - 8) })

        statusDot = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(LinkState.READY.dot) } }
        statusLabel = TextView(this).apply {
            textSize = 11f; setTextColor(0xffb9b6ae.toInt()); letterSpacing = 0.18f; isAllCaps = true
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(16), dp(7))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0x66000000); setStroke(dp(1), 0x33e9dcb8) }
            addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(9) })
            addView(statusLabel)
        }
        (home as FrameLayout).addView(statusPill, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(STATUS_MARGIN) })
        updateLink()
    }

    private fun showResult(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Done", null).show()
    }

    @SuppressLint("MissingPermission")
    private fun choosePreferredPhone() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            showResult("Bluetooth permission", "Tap Connect to iPhone on Home to grant Bluetooth access first.")
            return
        }
        val phones = BluetoothAdapter.getDefaultAdapter()?.bondedDevices.orEmpty().sortedBy { it.name.orEmpty() }
        if (phones.isEmpty()) { showResult("Choose iPhone", "No paired phones. Use Pair iPhone first."); return }
        AlertDialog.Builder(this).setTitle("Choose iPhone")
            .setItems(phones.map { it.name ?: "Paired phone" }.toTypedArray()) { _, index ->
                getSharedPreferences("legacy", MODE_PRIVATE).edit().putString("phone", phones[index].address).apply()
                showStatus("Saved ${phones[index].name ?: "iPhone"} for the next connection")
            }.setNegativeButton("Cancel", null).show()
    }

    @SuppressLint("MissingPermission", "PrivateApi")
    internal fun readinessSummary(): String {
        val bluetooth = try {
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) "Permission needed"
            else BluetoothAdapter.getDefaultAdapter()?.let { adapter ->
                val count = adapter.bondedDevices.orEmpty().size
                "${if (adapter.isEnabled) "On" else "Off"} · $count paired device(s)"
            } ?: "Unavailable"
        } catch (_: SecurityException) { "Permission needed" }
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ap = runCatching { wifi?.javaClass?.getMethod("getWifiApState")?.invoke(wifi) as? Int }.getOrNull()
        val apLabel = when (ap) { 10 -> "Stopping"; 11 -> "Off"; 12 -> "Starting"; 13 -> "On"; 14 -> "Failed"; else -> "Not reported by firmware" }
        val usbHost = packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        return "Bluetooth: $bluetooth\nTablet hotspot: $apLabel\nWi-Fi client: ${if (wifi?.isWifiEnabled == true) "On" else "Off (normal while hosting)"}\nUSB host support: ${if (usbHost) "Yes" else "No"}\nCarPlay session: ${if (carPlayActive) "Connected" else "Not connected"}\n\n" +
            "Default connection: $HOTSPOT_SSID hotspot + Bluetooth. Keep Wi-Fi and Bluetooth enabled on the iPhone.\n\n" +
            "Internet test: while connected, search Google Maps on the iPhone for a new destination. The iPhone needs cellular data; the tablet hotspot provides only the local CarPlay link."
    }

    private fun showReadinessChecks() = showResult("Connection checks", readinessSummary())

    private fun showAppInformation() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        showResult("About Cadillac CTS Dashboard", "Cadillac CTS Dashboard $version\n${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n\nCarPlay video: H.264 · ${VideoModes.resolution(getSharedPreferences("legacy", MODE_PRIVATE)).label} · up to ${VideoModes.fps(getSharedPreferences("legacy", MODE_PRIVATE))} fps\nConnection: tablet hotspot + Bluetooth, existing Wi-Fi, or USB OTG\nDisplay: automatic fullscreen, controls hidden during CarPlay\nMicrophone / Siri: unavailable in this experimental tablet build\n\nCadillac artwork sources and Unslop design credit are included with the source code.")
    }

    private fun testSound() {
        try {
            testTone?.release()
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 30)
            testTone = tone
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 250)
            main.postDelayed({ if (testTone === tone) { tone.release(); testTone = null } }, 600)
            Toast.makeText(this, "Test tone uses the tablet's media volume", Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) { showResult("Test sound", "Android could not open the media audio output.") }
    }

    private fun awaitTabletHotspot(ap: TabletHotspot, deadline: Long) {
        if (destroyed || tabletHotspot !== ap) return
        if (ap.isReady()) { startConnection(); return }
        if (android.os.SystemClock.elapsedRealtime() >= deadline) {
            stopConnection()
            connectionFailure("The tablet hotspot did not become ready. Try Connect to iPhone again.")
            return
        }
        main.postDelayed({ awaitTabletHotspot(ap, deadline) }, 250)
    }

    @Suppress("DEPRECATION")
    private fun configureExistingWifi(phone: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            showStatus("Bluetooth permission is needed. Tap Start wireless to grant it.")
            return
        }
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val currentSsid = wifi?.connectionInfo?.ssid?.removeSurrounding("\"")
            ?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isBlank() }
        if (currentSsid == null) { showStatus("Connect this tablet and your iPhone to the same Wi-Fi in Android settings first."); return }
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val ssid = TextView(this).apply { text = "Wi-Fi network: $currentSsid" }
        val password = EditText(this).apply {
            hint = "Wi-Fi password (blank for an open network)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            if (prefs.getString("wifi_ssid", null) == currentSsid) setText(prefs.getString("wifi_password", ""))
        }
        fields.addView(TextView(this).apply { text = "Use the Wi-Fi already connected on both devices. This does not change the tablet's Wi-Fi connection." })
        fields.addView(ssid); fields.addView(password)
        val dialog = AlertDialog.Builder(this).setTitle("Wireless CarPlay · ${phone.name ?: "iPhone"}")
            .setView(fields).setPositiveButton("Connect", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val network = currentSsid
                val secret = password.text.toString()
                if (secret.isNotEmpty() && secret.length !in 8..63) { password.error = "WPA2 passwords must be 8–63 characters"; return@setOnClickListener }
                wirelessSsid = network; wirelessPassword = secret; selectedPhone = phone.address; wireless = true
                prefs.edit().putString("wifi_ssid", network).putString("wifi_password", secret)
                    .putString("phone", phone.address).apply()
                dialog.dismiss()
                startConnection()
            }
        }
        dialog.show()
    }

    @Deprecated("API-22 activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BLUETOOTH_REQUEST) {
            if (resultCode == RESULT_OK) prepareWireless() else showStatus("Bluetooth must be on for wireless CarPlay.")
        }
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) startConnection()
            else showStatus("Connection permission was declined. Tap Start wired to try again.")
        }
    }

    private fun startConnection() {
        if (controller != null || destroyed) return
        try {
            installAuthentication()
            val identity = loadIdentity()
            val address = MessageDigest.getInstance("SHA-256").digest(identity.publicKey).take(6).toByteArray()
            address[0] = ((address[0].toInt() and 0xfc) or 2).toByte()
            val deviceId = address.joinToString(":") { "%02X".format(it.toInt() and 255) }
            val bluetoothMac = lollipopBluetoothAddress(deviceId)
            val videoPrefs = getSharedPreferences("legacy", MODE_PRIVATE)
            val videoMode = VideoModes.resolution(videoPrefs)
            val videoRate = VideoModes.fps(videoPrefs)
            val config = AirPlayConfig(
                deviceName = "DiPlay Tablet", deviceId = deviceId,
                btMac = bluetoothMac,
                sourceVersion = "220.68", main = AirPlayDisplayConfig(videoMode.width, videoMode.height, fps = videoRate),
                microphone = false, hevc = false, manufacturer = "DiPlay", model = "Legacy Tablet",
            )
            val renderer = AndroidMediaSink(
                surface = video.holder.surface.takeIf { it.isValid }, videoWidth = videoMode.width, videoHeight = videoMode.height,
                context = this, mainVideoFrameRate = videoRate,
                onAudioDiagnostic = { Log.i(TAG, it) },
            )
            sink = renderer
            val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
            val pairings = PairingStore { id, key ->
                prefs.edit().putString("pair:$id", Base64.encodeToString(key, Base64.NO_WRAP)).apply()
            }
            prefs.all.filterKeys { it.startsWith("pair:") }.forEach { (id, key) ->
                (key as? String)?.let { pairings.save(id.removePrefix("pair:"), Base64.decode(it, Base64.NO_WRAP)) }
            }
            val runtime = CarPlayRuntimeConfig(
                mfiTarget = MfiTarget.LOCAL,
                transport = if (wireless) CarPlayTransport.WIRELESS else CarPlayTransport.WIRED,
                wirelessHotspotMode = if (hotspotMode) WirelessHotspotMode.MANUAL else WirelessHotspotMode.EXISTING_WIFI,
                manualHotspotSsid = if (hotspotMode) wirelessSsid else null,
                manualHotspotPassphrase = if (hotspotMode) wirelessPassword else null,
                manualHotspotBand = ManualHotspotBand.GHZ_2_4,
                wirelessBluetoothDeviceAddress = selectedPhone,
                existingWifiSsid = wirelessSsid,
                existingWifiPassphrase = wirelessPassword,
                identification = Iap2IdentificationConfig(
                    name = "DiPlay", modelIdentifier = "LegacyTablet", manufacturer = "DiPlay",
                    serialNumber = "DIPLAY-" + deviceId.replace(":", ""),
                    firmwareVersion = "0.1", hardwareVersion = "1.0", carPlayUsbInterfaceNumber = 3,
                ), hostMac = address, label = "DiPlay Tablet", hostName = "diplay-tablet",
                availableCurrentMilliAmps = 500,
            )
            controller = CarPlayController(
                context = this, config = runtime, airPlayConfig = config, identity = identity,
                pairings = pairings, media = CarPlayMediaEngine(renderer, microphoneEnabled = false),
                listener = object : AirPlaySessionListener {
                    override val wantsTrace = false
                    override fun onSessionActive(session: AirPlaySession) { setCarPlayActive(true); showStatus("CarPlay connected") }
                    override fun onSessionEnded(session: AirPlaySession) { setCarPlayActive(false); showStatus("iPhone disconnected."); main.post { scheduleRetry() } }
                    override fun onTransportError(message: String) { showStatus(message) }
                    override fun onDebugLog(message: String) { diagnostic(message) }
                },
                reportStatus = { value ->
                    if (value is CarPlayStatus.Failed) { setCarPlayActive(false); main.post { connectionFailure(value.message) } }
                    showStatus(if (value is CarPlayStatus.IphoneNotListed) "No iPhone detected. ${usbSummary()}" else connectionStatus(value))
                },
                loadPairRecord = { loadPairRecord() },
                savePairRecord = { savePairRecord(it) },
                clearPairRecord = { getSharedPreferences("legacy", MODE_PRIVATE).edit().remove("lockdown").apply() },
            ).also { it.start() }
        } catch (error: Throwable) {
            Log.e(TAG, "Connection startup failed", error)
            stopConnection()
            showStatus("Cannot start: ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun lollipopBluetoothAddress(fallback: String): String {
        // On API 22 this needs the install-time BLUETOOTH permission. API 23+ restricts
        // the hardware address to privileged apps; never request it on those versions.
        if (!wireless || Build.VERSION.SDK_INT >= 23) return fallback
        return try { BluetoothAdapter.getDefaultAdapter()?.address ?: fallback }
        catch (_: SecurityException) { fallback }
    }

    private fun installAuthentication() {
        val target = File(noBackupFilesDir, LocalMfiAuthenticationClient.DIRECTORY)
        if (!target.exists()) {
            val staging = File(noBackupFilesDir, "local-auth-staging")
            staging.mkdirs()
            try {
                for (name in listOf("identity.pk8", "certificate.p7b")) {
                    assets.open("offline-mfi/$name").use { input -> File(staging, name).outputStream().use(input::copyTo) }
                }
                LocalMfiAuthenticationClient.load(staging)
                check(staging.renameTo(target)) { "Could not prepare local authentication" }
            } finally { staging.deleteRecursively() }
        }
        LocalMfiAuthenticationClient.load(target)
    }

    private fun loadIdentity(): AirPlayIdentity {
        val prefs = getSharedPreferences("legacy", MODE_PRIVATE)
        val privateKey = prefs.getString("private", null)
        val publicKey = prefs.getString("public", null)
        val pairingId = prefs.getString("identity", null)
        if (privateKey != null && publicKey != null && pairingId != null) {
            return AirPlayIdentity(Base64.decode(privateKey, Base64.NO_WRAP), Base64.decode(publicKey, Base64.NO_WRAP), pairingId)
        }
        return AirPlayIdentity.generate().also {
            prefs.edit().putString("private", Base64.encodeToString(it.privateKey, Base64.NO_WRAP))
                .putString("public", Base64.encodeToString(it.publicKey, Base64.NO_WRAP))
                .putString("identity", it.pairingId).apply()
        }
    }

    private fun savePairRecord(record: LockdownPairRecord) {
        val json = JSONObject().put("hostId", record.hostId).put("systemBuid", record.systemBuid)
            .put("wifiMacAddress", record.wifiMacAddress)
        mapOf("devicePublicKeyPem" to record.devicePublicKeyPem, "deviceCertificatePem" to record.deviceCertificatePem,
            "hostPrivateKeyPem" to record.hostPrivateKeyPem, "hostCertificatePem" to record.hostCertificatePem,
            "rootPrivateKeyPem" to record.rootPrivateKeyPem, "rootCertificatePem" to record.rootCertificatePem)
            .forEach { (name, bytes) -> json.put(name, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
        getSharedPreferences("legacy", MODE_PRIVATE).edit().putString("lockdown", json.toString()).apply()
    }

    private fun loadPairRecord(): LockdownPairRecord? = runCatching {
        val saved = getSharedPreferences("legacy", MODE_PRIVATE).getString("lockdown", null) ?: return null
        val json = JSONObject(saved)
        fun bytes(name: String) = Base64.decode(json.getString(name), Base64.NO_WRAP)
        LockdownPairRecord.restore(json.getString("hostId"), json.getString("systemBuid"), json.getString("wifiMacAddress"),
            bytes("devicePublicKeyPem"), bytes("deviceCertificatePem"), bytes("hostPrivateKeyPem"),
            bytes("hostCertificatePem"), bytes("rootPrivateKeyPem"), bytes("rootCertificatePem"))
    }.getOrNull()

    private fun forwardTouch(view: View, event: MotionEvent): Boolean {
        if (view.width == 0 || view.height == 0) return false
        val contacts = (0 until event.pointerCount).map { i ->
            val released = event.actionMasked == MotionEvent.ACTION_CANCEL ||
                event.actionMasked == MotionEvent.ACTION_UP ||
                (event.actionMasked == MotionEvent.ACTION_POINTER_UP && i == event.actionIndex)
            AirPlayContact(event.getPointerId(i), (event.getX(i) / view.width).toDouble().coerceIn(0.0, 1.0),
                (event.getY(i) / view.height).toDouble().coerceIn(0.0, 1.0), !released)
        }
        controller?.sendTouch(contacts)
        if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
        return true
    }

    private fun showStatus(message: String) {
        diagnostic(message)
        main.post { if (!destroyed) status.text = message }
    }

    private fun usbSummary(): String {
        val usb = getSystemService(Context.USB_SERVICE) as? UsbManager
        val devices = usb?.deviceList?.values.orEmpty()
        return if (devices.isEmpty()) "Android sees no USB devices. Check the OTG adapter, data cable, and USB power."
        else "Android sees ${devices.size} USB device(s): " + devices.joinToString { device ->
            "%04x:%04x (access=%s)".format(device.vendorId, device.productId, usb!!.hasPermission(device))
        }
    }

    private fun diagnostic(message: String) {
        Log.i(TAG, message)
        // The tablet is disconnected from PC ADB during an OTG test. Keep bounded local evidence.
        runCatching {
            synchronized(diagnosticLock) {
                val log = File(filesDir, "legacy-diagnostic.txt")
                if (log.length() > 512 * 1024) log.writeText("")
                log.appendText("${System.currentTimeMillis()} $message\n")
            }
        }
    }

    private fun stopConnection() {
        setCarPlayActive(false)
        controller?.let { runCatching { it.close() } }
        controller = null
        sink?.let { runCatching { it.close() } }
        sink = null
        tabletHotspot?.close()
        tabletHotspot = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) { sink?.setSurface(110, holder.surface) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { sink?.setSurface(110, holder.surface) }
    override fun surfaceDestroyed(holder: SurfaceHolder) { sink?.clearSurface(110, holder.surface) }
    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        phoneReceiver?.let { runCatching { unregisterReceiver(it) } }
        testTone?.release()
        testTone = null
        stopConnection()
        led.onChange = null
        led.close()
        super.onDestroy()
    }

    companion object {
        private const val PAGE_PADDING = 24
        private const val CONTENT_WIDTH = 960
        private const val BLOCK_GAP = 12
        private const val CARD_PADDING = 16
        private const val CARD_RADIUS = 16
        private const val BUTTON_HEIGHT = 56
        private const val HOME_HEADER_HEIGHT = 88
        private const val CONNECT_WIDTH = 440
        private const val CONNECT_HEIGHT = 96
        private const val ICON_TARGET = 56
        private const val HERO_LOGO_WIDTH = 232
        private const val HERO_LOGO_HEIGHT = 159
        private const val LIGHTING_BUTTON = 96
        private const val STATUS_MARGIN = 72
        private const val AUTO_START_DELAY_MS = 1500L
        private const val LIGHTING_FADE_MS = 260L
        private const val PAGE_IN_MS = 220L
        private const val PAGE_SLIDE = 28
        private const val LIGHTING_DEFAULT_TINT = 0xffa000ff.toInt()
        private const val LIGHTING_OFF_TINT = 0xff3a3d42.toInt()
        private const val HERO_ACTION_GAP = 32
        private const val SECTION_GAP = 24
        private const val ROW_GAP = 8
        private const val TEXT_LINE_GAP = 4
        private const val SETTINGS_ROW_HEIGHT = 88
        private const val SPLASH_HOLD_MS = 500L
        private const val SPLASH_SHORT_HOLD_MS = 150L
        private const val LINE_HEIGHT = 24
        private const val LED_SWATCH_MARGIN = 78
        private const val IDLE_DIM_ALPHA = 0.98f
        private const val IDLE_FADE_MS = 1100L
        private const val IDLE_LOGO_SCALE = 1.45f
        private const val WAKE_FADE_MS = 350L
        private const val LINE_MARGIN = 36
        private const val SPLASH_MAX_MS = 8000L
        private const val SPLASH_WORDMARK_WIDTH = 224
        private const val SPLASH_WORDMARK_HEIGHT = 60
        private const val CREST_WIDTH = 72
        private const val CREST_HEIGHT = 28
        private const val WORDMARK_WIDTH = 128
        private const val WORDMARK_HEIGHT = 36
        private const val BRAND_HEIGHT = 56
        private const val TRIM_SEGMENT = 24
        private val BACKGROUND = 0xff080a0c.toInt()
        private val CARD = 0xff15181c.toInt()
        private val ACCENT = 0xffc4b28b.toInt()
        private val FOREGROUND = 0xffe9e7e2.toInt()
        private val MUTED = 0xffa6aaaf.toInt()
        private val HAIRLINE = 0xff34383e.toInt()
        private const val TAG = "DiPlay-Legacy"
        private const val HOTSPOT_SSID = "Cadillac CTS"
        private const val VPN_REQUEST = 101
        private const val BLUETOOTH_REQUEST = 102
        private const val BLUETOOTH_PERMISSION = 103
        internal fun connectionStatus(value: CarPlayStatus): String = when (value) {
            is CarPlayStatus.Failed -> value.message
            CarPlayStatus.WaitingForIphone -> "Connect and unlock the iPhone using USB OTG. Accept Trust and USB permission prompts."
            CarPlayStatus.RequestingIphonePermission -> "Allow Cadillac CTS Dashboard to access the connected iPhone."
            CarPlayStatus.Pairing -> "Pairing with iPhone. Accept Trust on the phone if asked."
            CarPlayStatus.RunningControl -> "Connected. Waiting for CarPlay video."
            CarPlayStatus.StartingHotspot -> "Preparing the Wi-Fi connection…"
            CarPlayStatus.WaitingForPairedIphone -> "Waiting for the paired iPhone. Keep Bluetooth on and the iPhone unlocked."
            CarPlayStatus.ConnectingBluetooth -> "Connecting to iPhone over Bluetooth…"
            CarPlayStatus.RunningWireless -> "Wireless setup running. Accept CarPlay on your iPhone if prompted."
            CarPlayStatus.WirelessActive, CarPlayStatus.WirelessActiveFallback -> "Wireless CarPlay connected"
            is CarPlayStatus.HotspotReady -> "Wi-Fi ready: ${value.ssid}. Connecting to iPhone…"
            else -> value.javaClass.simpleName.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }
    }
}
