package com.shihab.diplay.legacy

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.SpannableString
import android.text.style.ReplacementSpan

/** Draws an icon inline with the button text, centred on the text line. */
internal class CentredIconSpan(private val icon: Drawable, private val size: Int, private val gap: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?) = size + gap

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val centre = (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        val iconTop = y + centre - size / 2f
        canvas.save()
        canvas.translate(x + gap, iconTop)
        icon.setBounds(0, 0, size, size)
        icon.draw(canvas)
        canvas.restore()
    }
}

/** A small arc that turns while the button waits; the owner invalidates the button each frame. */
internal class SpinnerDrawable(private val colour: Int, private val stroke: Float) : Drawable() {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = colour; alpha = 60 }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = colour; strokeCap = Paint.Cap.ROUND }
    private val oval = RectF()

    override fun draw(canvas: Canvas) {
        val inset = stroke / 2f + 1f
        oval.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        canvas.drawOval(oval, ring)
        val turn = (SystemClock.uptimeMillis() % 900L) / 900f
        canvas.drawArc(oval, turn * 360f - 90f, 100f, false, arc)
    }

    override fun setAlpha(alpha: Int) { arc.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { arc.colorFilter = colorFilter }
    @Deprecated("Drawable opacity compatibility") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

internal object ConnectLabel {
    /** "Connect" followed by the Apple logo, or "Connecting" followed by a spinner. */
    fun build(word: String, icon: Drawable, size: Int, gap: Int): CharSequence =
        SpannableString("$word ￼").apply { setSpan(CentredIconSpan(icon, size, gap), length - 1, length, 0) }
}
