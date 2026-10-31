package com.shihab.diplay.legacy

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * A thin brightwork line in the crest colours (red, gold, silver, blue) that fades out at both ends,
 * with a faint highlight drifting along it. It animates only while it is on screen.
 */
internal class CadillacLineView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenShader = LinearGradient(0f, 0f, 1f, 0f,
        intArrayOf(Color.TRANSPARENT, 0xb0ffffff.toInt(), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private var phase = 0f
    private var animator: ValueAnimator? = null

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val colours = intArrayOf(Color.TRANSPARENT, RED, GOLD, SILVER, BLUE, Color.TRANSPARENT)
        val shader = LinearGradient(0f, 0f, w.toFloat(), 0f, colours, floatArrayOf(0f, 0.16f, 0.4f, 0.6f, 0.84f, 1f), Shader.TileMode.CLAMP)
        line.shader = shader
        band.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cy = height / 2f
        val breathe = 0.5f + 0.1f * Math.sin(phase * 2 * Math.PI).toFloat()
        band.alpha = (255 * 0.10f * breathe / 0.5f).toInt()
        canvas.drawRect(0f, cy - 5f * density, w, cy + 5f * density, band)
        line.alpha = (255 * breathe).toInt()
        canvas.drawRect(0f, cy - density, w, cy + density, line)
        // The sheen travels left to right and is wider than the screen share it lights.
        val sheenWidth = w * 0.22f
        val x = -sheenWidth + (w + sheenWidth) * phase
        matrix.setScale(sheenWidth, 1f)
        matrix.postTranslate(x, 0f)
        sheenShader.setLocalMatrix(matrix)
        sheen.shader = sheenShader
        sheen.alpha = 120
        canvas.drawRect(maxOf(0f, x), cy - density, minOf(w, x + sheenWidth), cy + density, sheen)
    }

    private fun updateAnimation() {
        val shouldRun = isAttachedToWindow && windowVisibility == VISIBLE && isShown
        if (shouldRun && animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 7000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (!shouldRun) {
            animator?.cancel(); animator = null
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }

    private companion object {
        val RED = 0xffc8102e.toInt()
        val GOLD = 0xffc9a64e.toInt()
        val SILVER = 0xffd7dade.toInt()
        val BLUE = 0xff2f6db5.toInt()
    }
}
