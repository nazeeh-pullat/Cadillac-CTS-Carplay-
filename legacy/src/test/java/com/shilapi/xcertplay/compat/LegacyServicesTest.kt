package com.shilapi.xcertplay.compat

import android.content.Context
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.shilapi.xcertplay.network.ExistingWifiManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22, 24])
class LegacyServicesTest {
    @Test fun usbLookupWorksOnLollipopAndNougat() {
        val context = RuntimeEnvironment.getApplication()
        assertSame(context.getSystemService(Context.USB_SERVICE), context.getSystemServiceCompat(UsbManager::class.java))
    }

    @Test fun existingWifiManagerCanInitializeOnLollipopAndNougat() {
        val manager = ExistingWifiManager(RuntimeEnvironment.getApplication(), "Test Wi-Fi", "")
        manager.close()
    }

    @Test fun audioTrackCanBeCreatedWithoutMarshmallowBuilder() {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val format = AudioFormat.Builder().setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        val track = buildAudioTrackCompat(attributes, format, 8192)
        try {
            assertEquals(AudioTrack.STATE_INITIALIZED, track.state)
            assertEquals(0, audioTrackUnderrunsCompat(track))
        } finally { track.release() }
    }
}

