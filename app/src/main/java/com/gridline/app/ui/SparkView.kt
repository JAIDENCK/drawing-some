package com.gridline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** A bare trend line: one value per session, oldest on the left. Lower is better. */
class SparkView(context: Context) : View(context) {
    private val dp = resources.displayMetrics.density
    private var values: List<Double> = emptyList()
    private var unit = ""
    private var decimals = 1

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.6f * dp; color = Colors.INK
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.INK }
    private val axis = Paint().apply { color = 0xFFD6D2CA.toInt(); strokeWidth = dp }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.MUTED; textSize = 11 * dp }

    fun set(values: List<Double>, unit: String, decimals: Int) {
        this.values = values; this.unit = unit; this.decimals = decimals
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (72 * dp).toInt())
    }

    override fun onDraw(c: Canvas) {
        if (values.size < 2) return
        val left = 44 * dp
        val right = width - 8 * dp
        val top = 8 * dp
        val bottom = height - 8 * dp
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 1e-9) 1.0 else hi - lo
        c.drawLine(left, bottom, right, bottom, axis)
        c.drawText("${f(hi, decimals)}$unit", 0f, top + 8 * dp, label)
        c.drawText("${f(lo, decimals)}$unit", 0f, bottom, label)
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = left + (right - left) * i / (values.size - 1)
            val y = (bottom - (bottom - top) * ((v - lo) / span)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            c.drawCircle(x, y, 2.2f * dp, dot)
        }
        c.drawPath(p, line)
    }
}
