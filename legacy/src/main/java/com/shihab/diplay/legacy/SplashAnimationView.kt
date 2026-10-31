package com.shihab.diplay.legacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View

/**
 * Plays the splash sprite sheet once over pure black and holds the last frame.
 * The sheet (about 16 MB decoded as RGB_565) is decoded on a worker thread so the window appears at
 * once; the timeline starts when it is ready. Nothing loops.
 */
internal class SplashAnimationView(context: Context) : View(context) {
    @Volatile private var sheet: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = RectF()
    private var startedAt = 0L
    private var speed = 1f

    private fun played() = ((SystemClock.elapsedRealtime() - startedAt) * speed).toLong()

    val finished get() = startedAt != 0L && sheet != null && played() >= SplashFrames.TOTAL_MS

    init { setBackgroundColor(Color.BLACK); contentDescription = "Cadillac startup animation" }

    fun start(playbackSpeed: Float = 1f) {
        speed = playbackSpeed
        Thread({
            val decoded = BitmapFactory.decodeResource(resources, R.drawable.splash_sheet,
                BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.RGB_565 })
            post { sheet = decoded; startedAt = SystemClock.elapsedRealtime(); postInvalidateOnAnimation() }
        }, "splash-decode").start()
    }

    private fun frameAt(elapsed: Long): Int {
        var remaining = elapsed
        SplashFrames.DURATIONS_MS.forEachIndexed { index, duration ->
            if (remaining < duration) return index
            remaining -= duration
        }
        return SplashFrames.DURATIONS_MS.lastIndex
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = sheet ?: return
        if (startedAt == 0L) return
        val index = frameAt(played())
        val column = index % SplashFrames.COLUMNS
        val row = index / SplashFrames.COLUMNS
        source.set(column * SplashFrames.FRAME_WIDTH, row * SplashFrames.FRAME_HEIGHT,
            (column + 1) * SplashFrames.FRAME_WIDTH, (row + 1) * SplashFrames.FRAME_HEIGHT)
        // Size the final crest to about a third of the width (never beyond 360dp) while keeping its source position.
        val density = resources.displayMetrics.density
        val scale = minOf(360f * density, width * 0.3f) / SplashFrames.CREST_WIDTH
        val cx = width / 2f + SplashFrames.OFFSET_X * scale
        val cy = height / 2f + SplashFrames.OFFSET_Y * scale
        val halfW = SplashFrames.FRAME_WIDTH * scale / 2f
        val halfH = SplashFrames.FRAME_HEIGHT * scale / 2f
        target.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        canvas.drawBitmap(bitmap, source, target, paint)
        if (!finished) postInvalidateOnAnimation()
    }
}
