package com.gridline.app.sketch

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import com.gridline.app.ui.Colors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Overlay of horizon + converging rays. Never part of the saved image. */
class PerspectiveGuide {
    enum class Mode(val label: String) { OFF("Off"), ONE("1-point"), TWO("2-point") }

    var mode = Mode.OFF
    private val one = PointF()
    private val left = PointF()
    private val right = PointF()
    private var docW = 1
    private var docH = 1

    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Colors.GUIDE_LIGHT
    }
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Colors.GUIDE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Colors.SET_BLUE
    }

    fun reset(w: Int, h: Int) {
        docW = w; docH = h
        one.set(w * 0.5f, h * 0.42f)
        left.set(w * 0.06f, h * 0.42f)
        right.set(w * 0.94f, h * 0.42f)
    }

    fun next() {
        mode = Mode.entries[(mode.ordinal + 1) % Mode.entries.size]
    }

    val handles: List<PointF>
        get() = when (mode) {
            Mode.OFF -> emptyList()
            Mode.ONE -> listOf(one)
            Mode.TWO -> listOf(left, right)
        }

    fun move(i: Int, x: Float, y: Float) {
        when (mode) {
            Mode.OFF -> {}
            Mode.ONE -> one.set(x, y)
            Mode.TWO -> {
                // Both vanishing points sit on the same horizon.
                if (i == 0) left.set(x, y) else right.set(x, y)
                left.y = y; right.y = y
            }
        }
    }

    fun draw(c: Canvas, scale: Float, dp: Float) {
        val hs = handles
        if (hs.isEmpty()) return
        rayPaint.strokeWidth = dp / scale
        horizonPaint.strokeWidth = 1.4f * dp / scale
        handlePaint.strokeWidth = 2f * dp / scale
        val hy = hs[0].y
        c.drawLine(-docW * 3f, hy, docW * 4f, hy, horizonPaint)
        for (vp in hs) fan(c, vp)
        if (mode == Mode.TWO) {
            val step = docW / 16f
            var x = step
            while (x < docW) { c.drawLine(x, 0f, x, docH.toFloat(), rayPaint); x += step }
        }
        for (vp in hs) {
            c.drawCircle(vp.x, vp.y, 9 * dp / scale, handlePaint)
            c.drawCircle(vp.x, vp.y, 1.5f * dp / scale, handlePaint)
        }
    }

    private fun fan(c: Canvas, vp: PointF) {
        val far = (docW + docH) * 4f
        val inside = vp.x in 0f..docW.toFloat() && vp.y in 0f..docH.toFloat()
        if (inside) {
            for (k in 0 until 36) {
                val a = k * PI / 18
                c.drawLine(vp.x, vp.y, vp.x + (cos(a) * far).toFloat(), vp.y + (sin(a) * far).toFloat(), rayPaint)
            }
            return
        }
        // VP outside the page: spread rays only across the page.
        val base = atan2(docH / 2.0 - vp.y, docW / 2.0 - vp.x)
        var lo = 0.0
        var hi = 0.0
        val corners = arrayOf(0.0 to 0.0, docW.toDouble() to 0.0, docW.toDouble() to docH.toDouble(), 0.0 to docH.toDouble())
        for ((cx, cy) in corners) {
            var d = atan2(cy - vp.y, cx - vp.x) - base
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            lo = min(lo, d); hi = max(hi, d)
        }
        val n = 24
        for (k in 0..n) {
            val a = base + lo + (hi - lo) * k / n
            c.drawLine(vp.x, vp.y, vp.x + (cos(a) * far).toFloat(), vp.y + (sin(a) * far).toFloat(), rayPaint)
        }
    }
}
