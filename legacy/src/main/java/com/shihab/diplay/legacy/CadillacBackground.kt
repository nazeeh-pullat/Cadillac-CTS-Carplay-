package com.shihab.diplay.legacy

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Static tonal shading and fine brightwork; no bitmap, blur or animation loop. */
internal class CadillacBackground(private val density: Float) : Drawable() {
    private val surface = Paint()
    private val trim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30c4b28b; strokeWidth = density }

    override fun onBoundsChange(bounds: Rect) {
        surface.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            intArrayOf(0xff080a0c.toInt(), 0xff1b2026.toInt(), 0xff080a0c.toInt()),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, surface)
        val inset = bounds.width() * 0.06f
        val y = bounds.height() * 0.8f
        canvas.drawLine(inset, y, bounds.width() * 0.24f, y, trim)
        canvas.drawLine(bounds.width() * 0.76f, y, bounds.width() - inset, y, trim)
    }
    override fun setAlpha(alpha: Int) { surface.alpha = alpha; trim.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { surface.colorFilter = colorFilter }
    @Deprecated("Drawable opacity compatibility") override fun getOpacity() = PixelFormat.OPAQUE
}
