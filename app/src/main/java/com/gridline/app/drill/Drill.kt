package com.gridline.app.drill

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.gridline.app.ink.Stroke
import com.gridline.app.ui.Colors
import java.util.Random

class MetricDef(val key: String, val label: String, val unit: String, val decimals: Int)

class Attempt(val values: Map<String, Double>)

class DrillEnv(val pxPerMm: Float, val dp: Float, val rnd: Random = Random()) {
    fun mm(px: Double) = px / pxPerMm
    fun px(mm: Float) = mm * pxPerMm
    fun r(a: Double, b: Double) = a + rnd.nextDouble() * (b - a)
}

/**
 * One exercise. The view hands it finished strokes; it decides when an attempt
 * is complete, scores it and draws targets plus feedback.
 */
abstract class Drill(protected val env: DrillEnv) {
    abstract val type: String
    val title: String get() = Drills.title(type)
    val metrics: List<MetricDef> get() = Drills.metrics(type)

    /** Multi-stroke drills (boxes) are scored when the user presses Check. */
    open val multiStroke: Boolean get() = false
    open val checked: Boolean get() = false
    open val guideLabel: String? get() = null
    open fun cycleGuide() {}

    var feedback: CharSequence = ""
        protected set

    protected var w = 0f
    protected var h = 0f

    fun layout(width: Float, height: Float) {
        if (width == w && height == h) return
        w = width; h = height
        newRound()
    }

    abstract fun newRound()

    /** Returns true when this stroke completes an attempt that should be scored now. */
    abstract fun addStroke(s: Stroke): Boolean
    open fun undoStroke(): Boolean = false

    /** Scores the current attempt. Null means it doesn't count (feedback says why). */
    abstract fun score(): Attempt?
    open fun discardLast() {}
    abstract fun draw(c: Canvas)
}

class Paints(val dp: Float) {
    val ink = stroke(Colors.INK, 2.2f * dp)
    val inkFaded = stroke(Colors.INK_FADED, 2.2f * dp)
    val target = stroke(Colors.INK, 1.6f * dp)
    val given = stroke(Colors.INK, 2.6f * dp)
    val guide = stroke(Colors.GUIDE, 1.2f * dp)
    val guideLight = stroke(Colors.GUIDE_LIGHT, 1f * dp)
    val ideal = stroke(Colors.GOOD, 1.6f * dp)
    val idealDashed = stroke(Colors.GOOD, 1.8f * dp).apply {
        pathEffect = DashPathEffect(floatArrayOf(7 * dp, 5 * dp), 0f)
    }
    val ext = stroke(Colors.GUIDE, 1f * dp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Colors.INK }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * dp; color = Colors.INK }
    val textHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * dp; color = Colors.PAPER; style = Paint.Style.STROKE; strokeWidth = 4 * dp
    }

    fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    fun path(s: Stroke): Path {
        val p = Path()
        if (s.count > 0) {
            p.moveTo(s.xs[0], s.ys[0])
            for (i in 1 until s.count) p.lineTo(s.xs[i], s.ys[i])
        }
        return p
    }

    fun label(c: Canvas, t: String, x: Float, y: Float, color: Int) {
        c.drawText(t, x, y, textHalo)
        text.color = color
        c.drawText(t, x, y, text)
    }
}

object Drills {
    val types = listOf("lines", "ellipses", "box2", "box1", "boxfree")
    val lengthNames = arrayOf("Short", "Normal", "Long")

    fun title(type: String) = when (type) {
        "lines" -> "Lines"
        "ellipses" -> "Ellipses"
        "box2" -> "Boxes · 2-point"
        "box1" -> "Boxes · 1-point"
        "boxfree" -> "Boxes · free"
        "sketch" -> "Sketch"
        else -> type
    }

    fun blurb(type: String) = when (type) {
        "lines" -> "Connect two points in one stroke. Measures wobble, bow and accuracy."
        "ellipses" -> "Draw an ellipse to a target. Measures evenness, proportions and tilt."
        "box2" -> "Finish a box toward two vanishing points. Shows where each line really goes."
        "box1" -> "Extend a front face back to one vanishing point."
        "boxfree" -> "Draw any box; the app checks whether your parallel edges agree."
        else -> ""
    }

    private val lineMetrics = listOf(
        MetricDef("wobble", "Wobble", "mm", 2),
        MetricDef("bow", "Bow", "mm", 1),
        MetricDef("ends", "End miss", "mm", 1),
        MetricDef("angle", "Angle", "°", 1),
    )
    private val ellipseMetrics = listOf(
        MetricDef("shape", "Shape error", "mm", 2),
        MetricDef("size", "Size off", "%", 0),
        MetricDef("ratio", "Ratio off", "", 2),
        MetricDef("tilt", "Tilt off", "°", 1),
        MetricDef("center", "Center off", "mm", 1),
    )
    private val boxMetrics = listOf(
        MetricDef("conv", "Avg angle error", "°", 1),
        MetricDef("worst", "Worst line", "°", 1),
        MetricDef("wobble", "Wobble", "mm", 2),
    )

    fun metrics(type: String): List<MetricDef> = when (type) {
        "lines" -> lineMetrics
        "ellipses" -> ellipseMetrics
        "sketch" -> emptyList()
        else -> boxMetrics
    }

    /** The one number tracked over time for each drill. */
    fun headline(type: String): MetricDef? = metrics(type).firstOrNull()

    fun reps(type: String, length: Int): Int {
        val t = when (type) {
            "lines" -> intArrayOf(10, 20, 40)
            "ellipses" -> intArrayOf(8, 15, 30)
            else -> intArrayOf(3, 5, 10)
        }
        return t[length.coerceIn(0, 2)]
    }

    fun warmupPlan(length: Int): String = when (length) {
        0 -> "lines:8,ellipses:6,box2:2"
        2 -> "lines:25,ellipses:20,box2:5"
        else -> "lines:15,ellipses:10,box2:3"
    }

    fun create(type: String, env: DrillEnv): Drill = when (type) {
        "lines" -> LineDrill(env)
        "ellipses" -> EllipseDrill(env)
        "box1" -> BoxDrill(env, BoxDrill.Mode.ONE)
        "boxfree" -> BoxDrill(env, BoxDrill.Mode.FREE)
        else -> BoxDrill(env, BoxDrill.Mode.TWO)
    }
}
