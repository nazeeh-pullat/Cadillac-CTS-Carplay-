package com.shihab.diplay.legacy

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Soft champagne light pooled behind the crest; static, so it costs one gradient fill. */
internal class SpotlightDrawable(private val centerY: Float = 0.4f, private val strength: Int = 0x3c) : Drawable() {
    private val paint = Paint()

    override fun onBoundsChange(bounds: Rect) {
        val radius = maxOf(bounds.width() * 0.5f, 1f)
        paint.shader = RadialGradient(bounds.exactCenterX(), bounds.top + bounds.height() * centerY, radius,
            intArrayOf((strength shl 24) or 0xc4b28b, (strength / 3 shl 24) or 0xc4b28b, 0x00c4b28b),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) { canvas.drawRect(bounds, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable opacity compatibility") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Static tonal shading and fine brightwork; no bitmap, blur or animation loop. */
internal class CadillacBackground(private val density: Float, private val glow: Boolean = true) : Drawable() {
    private val surface = Paint()
    private val trim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30c4b28b; strokeWidth = density }
    private val spotlight = SpotlightDrawable()

    override fun onBoundsChange(bounds: Rect) {
        surface.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            intArrayOf(0xff080a0c.toInt(), 0xff1b2026.toInt(), 0xff080a0c.toInt()),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
        spotlight.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, surface)
        if (glow) spotlight.draw(canvas)
        val inset = bounds.width() * 0.06f
        val y = bounds.height() * 0.86f
        canvas.drawLine(inset, y, bounds.width() * 0.3f, y, trim)
        canvas.drawLine(bounds.width() * 0.7f, y, bounds.width() - inset, y, trim)
    }
    override fun setAlpha(alpha: Int) { surface.alpha = alpha; trim.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { surface.colorFilter = colorFilter }
    @Deprecated("Drawable opacity compatibility") override fun getOpacity() = PixelFormat.OPAQUE
}
