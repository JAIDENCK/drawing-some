package com.gridline.app.drill

import android.graphics.Canvas
import com.gridline.app.geom.V
import com.gridline.app.geom.analyzeLine
import com.gridline.app.geom.lineAngleDeg
import com.gridline.app.ink.Stroke
import com.gridline.app.ui.f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Two target points; connect them with one straight stroke. */
class LineDrill(env: DrillEnv) : Drill(env) {
    override val type = "lines"
    private val pt = Paints(env.dp)
    private var a = V(0.0, 0.0)
    private var b = V(0.0, 0.0)
    private var pending: Stroke? = null
    private var last: Last? = null

    private class Last(val a: V, val b: V, val stroke: Stroke)

    override fun newRound() {
        val m = min(w, h).toDouble()
        val margin = m * 0.08
        repeat(100) {
            val ax = env.r(margin, w - margin)
            val ay = env.r(margin, h - margin)
            val ang = env.r(0.0, 2 * PI)
            val len = env.r(m * 0.25, max(w, h) * 0.6)
            val bx = ax + cos(ang) * len
            val by = ay + sin(ang) * len
            if (bx in margin..(w - margin) && by in margin..(h - margin)) {
                a = V(ax, ay); b = V(bx, by)
                return
            }
        }
        a = V(w * 0.25, h * 0.5); b = V(w * 0.75, h * 0.5)
    }

    override fun addStroke(s: Stroke): Boolean {
        if (s.count < 3 || s.pathLength() < env.px(5f)) {
            feedback = "Too short, not counted."
            return false
        }
        pending = s
        return true
    }

    override fun score(): Attempt? {
        val s = pending ?: return null
        pending = null
        val sh = analyzeLine(s.xd(), s.yd())
        val endsPx = min(
            (sh.start - a).len + (sh.end - b).len,
            (sh.start - b).len + (sh.end - a).len,
        ) / 2
        val angle = abs(lineAngleDeg(b - a, sh.end - sh.start))
        val wobble = env.mm(sh.wobble)
        val bow = env.mm(abs(sh.bow))
        val ends = env.mm(endsPx)
        val lenMm = env.mm(sh.length)
        val secs = s.durationMs / 1000.0

        val tip = when {
            secs > 1.2 && wobble > 0.35 ->
                "Slow and shaky. Ghost the motion a few times, then commit in one quicker pass."
            bow > max(1.0, lenMm * 0.015) ->
                "It bows. Usually the wrist or elbow is pivoting; move the whole arm from the shoulder."
            wobble > 0.5 ->
                "Shaky. Go a little faster. Confidence first, accuracy follows."
            ends > max(3.0, lenMm * 0.03) ->
                "Straight, but it missed the points. Watch where you're going, not the pen tip."
            else -> "Clean."
        }
        feedback = "Wobble ${f(wobble, 2)} mm · Bow ${f(bow, 1)} mm · Ends ${f(ends, 1)} mm · " +
            "Angle ${f(angle, 1)}° · ${f(secs, 2)} s\n$tip"
        last = Last(a, b, s)
        return Attempt(mapOf("wobble" to wobble, "bow" to bow, "ends" to ends, "angle" to angle))
    }

    override fun discardLast() {
        last = null
        feedback = "Last attempt discarded."
    }

    override fun draw(c: Canvas) {
        last?.let { l ->
            c.drawLine(l.a.x.toFloat(), l.a.y.toFloat(), l.b.x.toFloat(), l.b.y.toFloat(), pt.ideal)
            c.drawPath(pt.path(l.stroke), pt.inkFaded)
        }
        target(c, a)
        target(c, b)
    }

    private fun target(c: Canvas, p: V) {
        val x = p.x.toFloat()
        val y = p.y.toFloat()
        c.drawCircle(x, y, 8 * pt.dp, pt.target)
        c.drawCircle(x, y, 2 * pt.dp, pt.fill)
    }
}
