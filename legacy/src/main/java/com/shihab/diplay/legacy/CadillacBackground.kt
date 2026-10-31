package com.shihab.diplay.legacy

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Pure black; the crest-coloured line (CadillacLineView) is the only decoration. */
internal class CadillacBackground : Drawable() {
    override fun draw(canvas: Canvas) { canvas.drawColor(0xff000000.toInt()) }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Drawable opacity compatibility") override fun getOpacity() = PixelFormat.OPAQUE
}
