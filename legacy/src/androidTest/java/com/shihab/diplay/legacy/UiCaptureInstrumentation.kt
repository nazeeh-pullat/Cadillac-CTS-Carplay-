package com.shihab.diplay.legacy

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import java.io.File
import java.util.Locale

/** Test-APK-only screenshot capture; never changes the tablet's system locale/font settings. */
@Suppress("DEPRECATION")
class UiCaptureInstrumentation : Instrumentation() {
    private var options = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        options = arguments ?: Bundle()
        start()
    }

    override fun callActivityOnCreate(activity: Activity, state: Bundle?) {
        val config = Configuration(activity.resources.configuration)
        config.fontScale = options.getString("fontScale", "1.0").toFloat()
        config.setLocale(Locale(options.getString("locale", "en")))
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        super.callActivityOnCreate(activity, state)
        activity.window.decorView.layoutDirection = config.layoutDirection
    }

    override fun onStart() {
        try {
            val activity = startActivitySync(Intent(targetContext, LegacyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            SystemClock.sleep(options.getString("delayMs", "3700").toLong())
            if (options.getString("page") == "settings") {
                runOnMainSync { (activity as LegacyActivity).openSettings() }
                waitForIdleSync()
                SystemClock.sleep(200)
            }
            val output = File(targetContext.getExternalFilesDir(null), "ui-capture.png")
            val screenshot = checkNotNull(uiAutomation.takeScreenshot())
            output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            runOnMainSync { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("screenshot", output.absolutePath) })
        } catch (error: Exception) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("error", error.toString()) })
        }
    }
}
