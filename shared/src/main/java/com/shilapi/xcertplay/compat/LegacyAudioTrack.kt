package com.shilapi.xcertplay.compat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build

/** The attributes constructor exists on Lollipop; AudioTrack.Builder starts at Marshmallow. */
@Suppress("DEPRECATION")
fun buildAudioTrackCompat(attributes: AudioAttributes, format: AudioFormat, bufferBytes: Int): AudioTrack =
    if (Build.VERSION.SDK_INT >= 23) {
        AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(bufferBytes).build()
    } else {
        AudioTrack(attributes, format, bufferBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
    }

/** Android 5.1 has no hardware-underrun counter; queue-based recovery still works. */
fun audioTrackUnderrunsCompat(track: AudioTrack?): Int =
    if (Build.VERSION.SDK_INT >= 24) track?.underrunCount ?: 0 else 0
