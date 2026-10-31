package com.shihab.diplay.legacy

import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.bluetooth.BluetoothAdapter
import android.view.View
import android.view.ViewGroup
import android.view.SurfaceView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBluetoothAdapter
import android.content.res.Configuration
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlertDialog
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22])
class LegacyActivityTest {
    @Suppress("DEPRECATION")
    @Config(sdk = [22], qualifiers = "w480dp-h320dp-land")
    @Test fun compactArabicLayoutAtLargeFontKeepsAllConnectionActionsReadable() {
        val resources = RuntimeEnvironment.getApplication().resources
        val original = Configuration(resources.configuration)
        val enlarged = Configuration(original).apply {
            fontScale = 1.3f
            locale = Locale("ar")
            setLayoutDirection(locale)
        }
        resources.updateConfiguration(enlarged, resources.displayMetrics)
        val host = Robolectric.buildActivity(LegacyActivity::class.java).setup()
        try {
            val activity = host.get()
            activity.openSettings()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 480, 320)
            val controls = buttons(root).filter { it.isShown }
            assertTrue(controls.any { it.text.toString().startsWith("Hotspot password\n") })
            assertEquals(View.LAYOUT_DIRECTION_RTL, activity.resources.configuration.layoutDirection)
            controls.forEach { button ->
                assertTrue("${button.text}: touch height", button.height >= 56)
                assertTrue("${button.text}: fits screen", button.width in 1..480)
                val textLayout = button.layout
                assertNotNull("${button.text}: text laid out", textLayout)
                for (line in 0 until textLayout.lineCount) assertEquals("${button.text}: not ellipsized", 0, textLayout.getEllipsisCount(line))
            }
        } finally {
            host.pause().stop().destroy()
            resources.updateConfiguration(original, resources.displayMetrics)
        }
    }
    @Suppress("DEPRECATION")
    @Test fun connectedCarPlayHidesDashboardAndBackRestoresControlsWithoutEndingSession() {
        val host = Robolectric.buildActivity(LegacyActivity::class.java).setup()
        try {
            val activity = host.get()
            val controls = buttons(activity.window.decorView)
            activity.setCarPlayActive(true)
            assertTrue(controls.none { it.isShown })
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
            val video = root.getChildAt(0) as SurfaceView
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, video.layoutParams.width)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, video.layoutParams.height)
            val flags = activity.window.decorView.systemUiVisibility
            assertTrue(flags and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
            assertTrue(flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
            assertTrue(flags and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY != 0)
            activity.onBackPressed()
            assertTrue(controls.single { it.text.toString().startsWith("Stop\n") }.isShown)
            assertTrue(controls.single { it.text == "Return to CarPlay" }.isShown)
            controls.single { it.text == "Return to CarPlay" }.performClick()
            assertTrue(controls.none { it.isShown })
            activity.setCarPlayActive(false)
            assertTrue(controls.single { it.contentDescription == "Connect to iPhone" }.isShown)
            assertFalse(controls.single { it.text == "Return to CarPlay" }.isShown)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun systemBarsAreHiddenAgainAfterReturningFromAnotherWindow() {
        val host = Robolectric.buildActivity(LegacyActivity::class.java).setup()
        try {
            host.get().window.decorView.systemUiVisibility = 0
            host.get().onWindowFocusChanged(true)
            assertTrue(host.get().window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY != 0)
        } finally { host.pause().stop().destroy() }
    }
    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }
    @Test fun hostLaunchesOnAndroid51AndCanStopWithoutAnIphone() {
        val host = Robolectric.buildActivity(LegacyActivity::class.java).setup()
        try {
            val controls = buttons(host.get().window.decorView)
            assertTrue(controls.single { it.contentDescription == "Connect to iPhone" }.isShown)
            assertFalse(controls.single { it.text.toString().startsWith("Start wired\n") }.isShown)
            host.get().openSettings()
            assertTrue(controls.single { it.text.toString().startsWith("Start wired\n") }.isShown)
            assertTrue(controls.single { it.text.toString().startsWith("Stop\n") }.performClick())
        } finally { host.pause().stop().destroy() }
    }

    private fun icons(view: View): List<ImageButton> = when (view) {
        is ImageButton -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { icons(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun homeHasOnePrimaryActionAndAccessibleSettingsGear() {
        val host = Robolectric.buildActivity(LegacyActivity::class.java).setup()
        try {
            val root = host.get().window.decorView
            assertEquals(listOf("Connect to iPhone"), buttons(root).filter { it.isShown }.map { (it.contentDescription ?: it.text).toString() })
            val gear = icons(root).single { it.contentDescription == "Settings" }
            assertTrue(gear.isShown)
            gear.performClick()
            assertTrue(buttons(root).single { it.text.toString().startsWith("Run connection checks\n") }.isShown)
            icons(root).single { it.contentDescription == "Home" }.performClick()
            assertEquals(1, buttons(root).count { it.isShown })
        } finally { host.pause().stop().destroy() }
    }

    @Test fun diagnosticChecksExplainBluetoothAndCellularRequirementsWithoutConnecting() {
        ShadowBluetoothAdapter.setIsBluetoothSupported(true)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(false)
        val host = Robolectric.buildActivity(LegacyActivity::class.java).setup()
        try {
            host.get().openSettings()
            buttons(host.get().window.decorView).single { it.text.toString().startsWith("Run connection checks\n") }.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val message = dialog.findViewById<TextView>(android.R.id.message).text.toString()
            assertTrue(message.contains("Bluetooth: Off"))
            assertTrue(message.contains("CarPlay session: Not connected"))
            assertTrue(message.contains("iPhone needs cellular data"))
            dialog.dismiss()
        } finally { host.pause().stop().destroy() }
    }

    @Test fun wirelessRequestsBluetoothWhenTheRadioIsOffOnAndroid51() {
        ShadowBluetoothAdapter.setIsBluetoothSupported(true)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(false)
        val host = Robolectric.buildActivity(LegacyActivity::class.java).setup()
        try {
            buttons(host.get().window.decorView).single { it.contentDescription == "Connect to iPhone" }.performClick()
            assertEquals(BluetoothAdapter.ACTION_REQUEST_ENABLE,
                shadowOf(host.get()).nextStartedActivityForResult.intent.action)
        } finally { host.pause().stop().destroy() }
    }
}
