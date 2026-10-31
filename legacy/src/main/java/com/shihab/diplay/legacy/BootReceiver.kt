package com.shihab.diplay.legacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Opens the dashboard when the tablet finishes booting, so no home screen is needed. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startActivity(Intent(context, LegacyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
