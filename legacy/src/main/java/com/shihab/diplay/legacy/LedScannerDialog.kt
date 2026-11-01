package com.shihab.diplay.legacy

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// Install-time permissions on Android 5.1 (see LedStrip); BLUETOOTH_SCAN/CONNECT exist only on Android 12+.
@android.annotation.SuppressLint("MissingPermission")
/** Lists nearby Bluetooth LE devices so the LED controller can be identified and saved. Lives in Settings only. */
internal class LedScannerDialog(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val onSaved: () -> Unit,
) {
    private data class Found(val address: String, var name: String?, var rssi: Int, var seen: Long)

    private val found = LinkedHashMap<String, Found>()
    private val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val header = TextView(activity).apply { textSize = 14f; setTextColor(0xffa6aaaf.toInt()) }
    private val main = Handler(Looper.getMainLooper())
    private var scanning = false
    private var dialog: AlertDialog? = null
    private val adapter get() = BluetoothAdapter.getDefaultAdapter()

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName ?: device.name
            main.post {
                val entry = found.getOrPut(device.address) { Found(device.address, name, result.rssi, 0) }
                if (!name.isNullOrBlank()) entry.name = name
                entry.rssi = result.rssi
                entry.seen = System.currentTimeMillis()
                render()
            }
        }
        override fun onScanFailed(errorCode: Int) { main.post { header.text = "Scan failed (code $errorCode). Turn Bluetooth off and on, then try again." ; scanning = false } }
    }

    fun show() {
        if (adapter?.isEnabled != true) {
            Toast.makeText(activity, "Turn Bluetooth on first.", Toast.LENGTH_LONG).show(); return
        }
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        body.addView(header)
        body.addView(ScrollView(activity).apply { addView(list) }, LinearLayout.LayoutParams(-1, dp(260)).apply { topMargin = dp(8) })
        dialog = AlertDialog.Builder(activity).setTitle("Find LED strip").setView(body)
            .setPositiveButton("Scan again", null).setNegativeButton("Close", null).create().also { d ->
                d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { startScan() } }
                d.setOnDismissListener { stopScan() }
                d.show()
            }
        startScan()
    }

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run { header.text = "This tablet cannot scan for Bluetooth LE devices right now."; return }
        stopScan()
        found.clear(); list.removeAllViews()
        header.text = "Scanning for 12 seconds. Switch the strip on and keep it close to the tablet."
        runCatching { scanner.startScan(callback); scanning = true }.onFailure { header.text = "Could not start the scan: ${it.javaClass.simpleName}" }
        main.postDelayed({ if (scanning) { stopScan(); render() } }, 12000)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
    }

    private fun looksLikeLedStrip(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return listOf("elk", "bledom", "led", "triones", "lotus", "lantern", "melk", "happy", "magic", "qhm", "ble-m", "sp10", "dream").any { n.contains(it) }
    }

    private fun render() {
        list.removeAllViews()
        val range = LedModes.scanRange(prefs)
        val entries = found.values.filter { it.rssi >= range.minRssi }
            .sortedWith(compareByDescending<Found> { it.rssi }.thenByDescending { looksLikeLedStrip(it.name) })
        val hidden = found.size - entries.size
        header.text = (if (scanning) "Scanning… " else "Scan finished. ") + "${entries.size} device(s) · range: ${range.label}" +
            (if (hidden > 0) " ($hidden weaker hidden)" else "") + ". Strongest first; tap your LED strip to save it."
        for (entry in entries.take(40)) {
            val likely = looksLikeLedStrip(entry.name)
            val row = Button(activity).apply {
                isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL; textSize = 15f
                text = "${entry.name ?: "(no name)"}${if (likely) "  ·  likely LED strip" else ""}\n${entry.address}  ·  ${entry.rssi} dBm"
                setTextColor(if (likely) 0xffc4b28b.toInt() else Color.WHITE)
                setOnClickListener { save(entry) }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun save(entry: Found) {
        stopScan()
        LedModes.saveStrip(prefs, entry.address, entry.name)
        dialog?.dismiss()
        Toast.makeText(activity, "Saved ${entry.name ?: entry.address}. Use Test colours to check it.", Toast.LENGTH_LONG).show()
        onSaved()
    }
}
