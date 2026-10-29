package com.gridline.app.sketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.gridline.app.ink.Ink
import com.gridline.app.ink.InkPredictor
import com.gridline.app.ink.Pen
import com.gridline.app.ink.Stroke
import com.gridline.app.ui.Colors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Free sketch canvas.
 *  - Pen draws (pressure -> width). Fingers pan/zoom (pinch) and drag guide handles.
 *  - Strokes live in document coordinates; a bitmap holds the rendered page.
 *  - Undo replays strokes over a base bitmap; very old strokes get flattened into it.
 */
class SketchView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    interface Listener {
        fun onStrokeCommitted()
        fun onUndoStateChanged()
    }

    enum class Tool { PEN, ERASER }

    var listener: Listener? = null
    var tool = Tool.PEN
    var color = Colors.INK
    var penSize = 5f
    var eraserSize = 36f
    var penOnly = true
    var showHover = true
    var swapButtons = false
    var predict: Boolean
        get() = predictor.enabled
        set(v) { predictor.enabled = v }
    val guide = PerspectiveGuide()

    private val dp = resources.displayMetrics.density
    private var docW = 0
    private var docH = 0
    private var base: Bitmap? = null
    private var baseCanvas: Canvas? = null
    private var layer: Bitmap? = null
    private var layerCanvas: Canvas? = null
    private var pendingBase: Bitmap? = null

    private val strokes = ArrayList<Stroke>()
    private val redoStack = ArrayList<Stroke>()
    private var current: Stroke? = null
    private var drawPointer = -1
    private var currentIsFinger = false

    private val matrix = Matrix()
    private val inverse = Matrix()
    private val pts = FloatArray(2)
    private val mvals = FloatArray(9)

    private val renderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paperPaint = Paint().apply { color = Color.WHITE }
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f * dp; color = Colors.MUTED
    }
    private val predictor = InkPredictor(context)

    private enum class G { NONE, PAN, PINCH, HANDLE }
    private var gesture = G.NONE
    private val gIds = IntArray(2)
    private var gCount = 0
    private val startMatrix = Matrix()
    private var startCx = 0f
    private var startCy = 0f
    private var startSpan = 1f
    private var handle = -1
    private val gesturePoint = FloatArray(3)

    private var hovering = false
    private var hoverX = 0f
    private var hoverY = 0f

    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Call before the view is laid out to restore a previous page. */
    fun loadBase(bmp: Bitmap) {
        pendingBase = bmp
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (layer == null) createDoc(pendingBase?.width ?: w, pendingBase?.height ?: h)
        fit()
    }

    private fun createDoc(w: Int, h: Int) {
        docW = max(1, w); docH = max(1, h)
        val b = Bitmap.createBitmap(docW, docH, Bitmap.Config.ARGB_8888)
        base = b
        baseCanvas = Canvas(b).also { c -> pendingBase?.let { c.drawBitmap(it, 0f, 0f, null) } }
        pendingBase = null
        val l = Bitmap.createBitmap(docW, docH, Bitmap.Config.ARGB_8888)
        layer = l
        layerCanvas = Canvas(l).also { it.drawBitmap(b, 0f, 0f, null) }
        guide.reset(docW, docH)
    }

    fun fit() {
        if (docW == 0 || width == 0) return
        val s = min(width.toFloat() / docW, height.toFloat() / docH)
        matrix.setScale(s, s)
        matrix.postTranslate((width - docW * s) / 2, (height - docH * s) / 2)
        matrix.invert(inverse)
        invalidate()
    }

    private fun scale(): Float {
        matrix.getValues(mvals)
        return mvals[Matrix.MSCALE_X]
    }

    // ---- drawing ---------------------------------------------------------

    override fun onDraw(c: Canvas) {
        c.drawColor(Colors.DESK)
        val l = layer ?: return
        val s = scale()
        c.save()
        c.concat(matrix)
        c.drawRect(0f, 0f, docW.toFloat(), docH.toFloat(), paperPaint)
        c.drawBitmap(l, 0f, 0f, bitmapPaint)
        current?.let { cur ->
            if (!cur.erase) {
                Ink.render(c, cur, livePaint)
                drawPrediction(c, cur)
            }
        }
        guide.draw(c, s, dp)
        c.restore()
        if (showHover && hovering && current == null) {
            val size = if (tool == Tool.ERASER) eraserSize else penSize
            c.drawCircle(hoverX, hoverY, max(3 * dp, size * s / 2), hoverPaint)
        }
    }

    private fun drawPrediction(c: Canvas, s: Stroke) {
        if (s.count == 0) return
        val pe = predictor.predict() ?: return
        val i = pe.findPointerIndex(drawPointer)
        if (i >= 0) {
            livePaint.strokeWidth = Ink.width(s.size, s.ps[s.count - 1])
            livePaint.color = Colors.withAlpha(s.color, 140)
            var lx = s.xs[s.count - 1]
            var ly = s.ys[s.count - 1]
            for (hh in 0..pe.historySize) {
                pts[0] = if (hh < pe.historySize) pe.getHistoricalX(i, hh) else pe.getX(i)
                pts[1] = if (hh < pe.historySize) pe.getHistoricalY(i, hh) else pe.getY(i)
                inverse.mapPoints(pts)
                c.drawLine(lx, ly, pts[0], pts[1], livePaint)
                lx = pts[0]; ly = pts[1]
            }
        }
        pe.recycle()
    }

    // ---- input -----------------------------------------------------------

    private fun eraseButton() = if (swapButtons) MotionEvent.BUTTON_STYLUS_SECONDARY else MotionEvent.BUTTON_STYLUS_PRIMARY
    private fun undoButton() = if (swapButtons) MotionEvent.BUTTON_STYLUS_PRIMARY else MotionEvent.BUTTON_STYLUS_SECONDARY

    private fun handleButton(button: Int) {
        if (button == undoButton() && current == null) undo()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                handleButton(e.actionButton)
                return true
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val tool = e.getToolType(i)
                if (Pen.isStylus(tool) || tool == MotionEvent.TOOL_TYPE_MOUSE) {
                    if (current != null && currentIsFinger) discardCurrent()
                    if (current == null) {
                        gesture = G.NONE
                        startStroke(e, i)
                    }
                } else if (current == null || currentIsFinger) {
                    val fingers = fingerCount(e, -1)
                    if (!penOnly && fingers == 1 && current == null && gesture == G.NONE &&
                        handleAt(e.getX(i), e.getY(i)) < 0
                    ) {
                        startStroke(e, i)
                    } else {
                        if (current != null) discardCurrent()
                        beginGesture(e, -1)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (current != null) addPoints(e) else if (gesture != G.NONE) updateGesture(e)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val i = e.actionIndex
                if (current != null && e.getPointerId(i) == drawPointer) {
                    addPoint(e, i, -1)
                    if ((e.flags and MotionEvent.FLAG_CANCELED) != 0) discardCurrent() else commitCurrent()
                } else if (gesture != G.NONE) {
                    if (e.actionMasked == MotionEvent.ACTION_UP) gesture = G.NONE else beginGesture(e, i)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                discardCurrent()
                gesture = G.NONE
            }
        }
        return true
    }

    private fun fingerCount(e: MotionEvent, exclude: Int): Int {
        var n = 0
        for (k in 0 until e.pointerCount) if (k != exclude && !Pen.isStylus(e.getToolType(k))) n++
        return n
    }

    private fun startStroke(e: MotionEvent, i: Int) {
        val tool = e.getToolType(i)
        val buttonErase = (e.buttonState and eraseButton()) != 0
        val erase = tool == MotionEvent.TOOL_TYPE_ERASER || buttonErase || this.tool == Tool.ERASER
        current = Stroke(color, if (erase) eraserSize else penSize, erase)
        drawPointer = e.getPointerId(i)
        currentIsFinger = !Pen.isStylus(tool) && tool != MotionEvent.TOOL_TYPE_MOUSE
        hovering = false
        requestUnbufferedDispatch(e)
        addPoint(e, i, -1)
        predictor.record(e)
        invalidate()
    }

    private fun addPoints(e: MotionEvent) {
        val i = e.findPointerIndex(drawPointer)
        if (i < 0) return
        for (hh in 0 until e.historySize) addPoint(e, i, hh)
        addPoint(e, i, -1)
        predictor.record(e)
        invalidate()
    }

    private fun addPoint(e: MotionEvent, i: Int, hh: Int) {
        val s = current ?: return
        pts[0] = if (hh < 0) e.getX(i) else e.getHistoricalX(i, hh)
        pts[1] = if (hh < 0) e.getY(i) else e.getHistoricalY(i, hh)
        val p = when {
            currentIsFinger -> 0.5f
            hh < 0 -> e.getPressure(i)
            else -> e.getHistoricalPressure(i, hh)
        }
        val t = if (hh < 0) e.eventTime else e.getHistoricalEventTime(hh)
        inverse.mapPoints(pts)
        val before = s.count
        s.add(pts[0], pts[1], p, t)
        // The eraser has to cut into the page bitmap to show its effect live.
        if (s.erase && s.count > before) layerCanvas?.let { Ink.render(it, s, renderPaint, s.count - 1) }
    }

    private fun commitCurrent() {
        val s = current ?: return
        current = null
        drawPointer = -1
        if (!s.erase) layerCanvas?.let { Ink.render(it, s, renderPaint) }
        pushStroke(s)
        listener?.onStrokeCommitted()
    }

    private fun discardCurrent() {
        val s = current ?: return
        current = null
        drawPointer = -1
        if (s.erase) rebuild()
        invalidate()
    }

    private fun pushStroke(s: Stroke) {
        strokes.add(s)
        redoStack.clear()
        while (strokes.size > MAX_UNDO) {
            val old = strokes.removeAt(0)
            baseCanvas?.let { Ink.render(it, old, renderPaint) }
        }
        listener?.onUndoStateChanged()
        invalidate()
    }

    // ---- gestures --------------------------------------------------------

    private fun beginGesture(e: MotionEvent, exclude: Int) {
        gCount = 0
        for (k in 0 until e.pointerCount) {
            if (k == exclude || Pen.isStylus(e.getToolType(k))) continue
            if (gCount < 2) gIds[gCount++] = e.getPointerId(k)
        }
        if (gCount == 0 || !centroid(e)) {
            gesture = G.NONE
            return
        }
        startMatrix.set(matrix)
        startCx = gesturePoint[0]; startCy = gesturePoint[1]; startSpan = max(1f, gesturePoint[2])
        gesture = if (gCount >= 2) {
            G.PINCH
        } else {
            handle = handleAt(startCx, startCy)
            if (handle >= 0) G.HANDLE else G.PAN
        }
    }

    private fun centroid(e: MotionEvent): Boolean {
        var sx = 0f; var sy = 0f; var n = 0
        val xs = FloatArray(2); val ys = FloatArray(2)
        for (k in 0 until gCount) {
            val idx = e.findPointerIndex(gIds[k])
            if (idx < 0) continue
            xs[n] = e.getX(idx); ys[n] = e.getY(idx)
            sx += xs[n]; sy += ys[n]; n++
        }
        if (n == 0) return false
        gesturePoint[0] = sx / n
        gesturePoint[1] = sy / n
        gesturePoint[2] = if (n >= 2) hypot(xs[0] - xs[1], ys[0] - ys[1]) else 1f
        return true
    }

    private fun updateGesture(e: MotionEvent) {
        if (!centroid(e)) return
        val cx = gesturePoint[0]
        val cy = gesturePoint[1]
        when (gesture) {
            G.PAN -> {
                matrix.set(startMatrix)
                matrix.postTranslate(cx - startCx, cy - startCy)
            }
            G.PINCH -> {
                startMatrix.getValues(mvals)
                val s0 = mvals[Matrix.MSCALE_X]
                val target = (s0 * gesturePoint[2] / startSpan).coerceIn(0.2f, 12f)
                val k = target / s0
                matrix.set(startMatrix)
                matrix.postScale(k, k, startCx, startCy)
                matrix.postTranslate(cx - startCx, cy - startCy)
            }
            G.HANDLE -> {
                pts[0] = cx; pts[1] = cy
                inverse.mapPoints(pts)
                guide.move(handle, pts[0], pts[1])
            }
            G.NONE -> return
        }
        matrix.invert(inverse)
        invalidate()
    }

    private fun handleAt(sx: Float, sy: Float): Int {
        val hs = guide.handles
        for (k in hs.indices) {
            pts[0] = hs[k].x; pts[1] = hs[k].y
            matrix.mapPoints(pts)
            if (hypot(pts[0] - sx, pts[1] - sy) < 32 * dp) return k
        }
        return -1
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
            handleButton(e.actionButton)
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    // ---- history ---------------------------------------------------------

    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.size - 1))
        rebuild()
        listener?.onUndoStateChanged()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val s = redoStack.removeAt(redoStack.size - 1)
        strokes.add(s)
        layerCanvas?.let { Ink.render(it, s, renderPaint) }
        invalidate()
        listener?.onUndoStateChanged()
    }

    /** Clearing is a stroke too, so Undo brings the page back. */
    fun clearPage() {
        val s = Stroke(Color.TRANSPARENT, 0f, clearAll = true)
        layerCanvas?.let { Ink.render(it, s, renderPaint) }
        pushStroke(s)
    }

    private fun rebuild() {
        val l = layer ?: return
        val lc = layerCanvas ?: return
        l.eraseColor(Color.TRANSPARENT)
        base?.let { lc.drawBitmap(it, 0f, 0f, null) }
        for (s in strokes) Ink.render(lc, s, renderPaint)
        invalidate()
    }

    /** Current page with transparency (for autosave). */
    fun snapshot(): Bitmap? = layer?.copy(Bitmap.Config.ARGB_8888, false)

    /** Current page on white (for export). */
    fun exportBitmap(): Bitmap? {
        val l = layer ?: return null
        val out = Bitmap.createBitmap(docW, docH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(l, 0f, 0f, null)
        return out
    }

    companion object {
        private const val MAX_UNDO = 150
    }
}
