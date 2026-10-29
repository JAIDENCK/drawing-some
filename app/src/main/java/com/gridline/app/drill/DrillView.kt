package com.gridline.app.drill

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.gridline.app.ink.InkPredictor
import com.gridline.app.ink.Pen
import com.gridline.app.ink.Stroke
import com.gridline.app.ui.Colors

/** Raw pen input for drills: no smoothing, no snapping. What you draw is what gets scored. */
class DrillView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    interface Listener {
        fun onStrokeStart()
        fun onStroke(s: Stroke)
        fun onPenButton(button: Int)
        fun onRoundReset()
    }

    var listener: Listener? = null
    var penOnly = true
    var showHover = true
    var predict: Boolean
        get() = predictor.enabled
        set(v) { predictor.enabled = v }

    private var drill: Drill? = null
    private val dp = resources.displayMetrics.density
    private var current: Stroke? = null
    private var pointer = -1
    private var hovering = false
    private var hoverX = 0f
    private var hoverY = 0f
    private val predictor = InkPredictor(context)

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.2f * dp
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Colors.INK
    }
    private val predPaint = Paint(inkPaint).apply { color = Colors.INK_FADED }
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f * dp; color = Colors.MUTED
    }

    fun bind(d: Drill) {
        drill = d
        if (width > 0) d.layout(width.toFloat(), height.toFloat())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        drill?.let {
            it.layout(w.toFloat(), h.toFloat())
            listener?.onRoundReset()
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Colors.PAPER)
        drill?.draw(c)
        current?.let { s ->
            for (i in 1 until s.count) c.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], inkPaint)
            drawPrediction(c, s)
        }
        if (showHover && hovering && current == null) c.drawCircle(hoverX, hoverY, 4 * dp, hoverPaint)
    }

    private fun drawPrediction(c: Canvas, s: Stroke) {
        if (s.count == 0) return
        val pe = predictor.predict() ?: return
        val i = pe.findPointerIndex(pointer)
        if (i >= 0) {
            var lx = s.xs[s.count - 1]
            var ly = s.ys[s.count - 1]
            for (hh in 0 until pe.historySize) {
                val x = pe.getHistoricalX(i, hh); val y = pe.getHistoricalY(i, hh)
                c.drawLine(lx, ly, x, y, predPaint); lx = x; ly = y
            }
            c.drawLine(lx, ly, pe.getX(i), pe.getY(i), predPaint)
        }
        pe.recycle()
    }

    private fun accepts(tool: Int) = Pen.isStylus(tool) || tool == MotionEvent.TOOL_TYPE_MOUSE || !penOnly

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                listener?.onPenButton(e.actionButton)
                return true
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                if (pointer == -1 && accepts(e.getToolType(i))) {
                    pointer = e.getPointerId(i)
                    requestUnbufferedDispatch(e)
                    current = Stroke(Colors.INK, 1f).also { add(it, e, i, -1) }
                    predictor.record(e)
                    hovering = false
                    listener?.onStrokeStart()
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val s = current
                if (s != null) {
                    val i = e.findPointerIndex(pointer)
                    if (i >= 0) {
                        for (hh in 0 until e.historySize) add(s, e, i, hh)
                        add(s, e, i, -1)
                        predictor.record(e)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val i = e.actionIndex
                val s = current
                if (s != null && e.getPointerId(i) == pointer) {
                    add(s, e, i, -1)
                    val canceled = (e.flags and MotionEvent.FLAG_CANCELED) != 0
                    current = null
                    pointer = -1
                    if (!canceled) listener?.onStroke(s)
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                current = null
                pointer = -1
                invalidate()
            }
        }
        return true
    }

    private fun add(s: Stroke, e: MotionEvent, i: Int, hh: Int) {
        if (hh < 0) s.add(e.getX(i), e.getY(i), e.getPressure(i), e.eventTime)
        else s.add(e.getHistoricalX(i, hh), e.getHistoricalY(i, hh), e.getHistoricalPressure(i, hh), e.getHistoricalEventTime(hh))
    }

    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (!Pen.isStylus(e.getToolType(0))) return super.onHoverEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hovering = true; hoverX = e.x; hoverY = e.y
            }
            MotionEvent.ACTION_HOVER_EXIT -> hovering = false
        }
        if (showHover) invalidate()
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
            listener?.onPenButton(e.actionButton)
            return true
        }
        return super.onGenericMotionEvent(e)
    }
}
