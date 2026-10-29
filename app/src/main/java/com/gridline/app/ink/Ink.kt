package com.gridline.app.ink

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import kotlin.math.max
import kotlin.math.pow

object Ink {
    private val clear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    /** Pressure curve: light touch still leaves a thin line, full pressure = full size. */
    fun width(size: Float, pressure: Float): Float =
        size * (0.15f + 0.85f * pressure.coerceIn(0f, 1f).pow(1.2f))

    /**
     * Draws [s] from point index [from] onward (0 = whole stroke). Ink width follows
     * pressure; the eraser keeps a constant width so it's predictable.
     */
    fun render(c: Canvas, s: Stroke, paint: Paint, from: Int = 0) {
        if (s.clearAll) {
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            return
        }
        paint.xfermode = if (s.erase) clear else null
        paint.color = if (s.erase) Color.BLACK else s.color
        paint.strokeCap = Paint.Cap.ROUND
        if (from == 0 && s.count == 1) {
            paint.style = Paint.Style.FILL
            val w = if (s.erase) s.size else width(s.size, s.ps[0])
            c.drawCircle(s.xs[0], s.ys[0], w / 2, paint)
        }
        paint.style = Paint.Style.STROKE
        for (i in max(1, from) until s.count) {
            paint.strokeWidth = if (s.erase) s.size else width(s.size, (s.ps[i - 1] + s.ps[i]) / 2)
            c.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], paint)
        }
        paint.xfermode = null
    }
}

object Pen {
    fun isStylus(tool: Int) =
        tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
}
