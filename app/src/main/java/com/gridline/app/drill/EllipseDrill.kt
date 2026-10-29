package com.gridline.app.drill

import android.graphics.Canvas
import android.graphics.Paint
import com.gridline.app.geom.Ellipse
import com.gridline.app.geom.axisDiffDeg
import com.gridline.app.geom.fitEllipse
import com.gridline.app.ink.Stroke
import com.gridline.app.ui.Colors
import com.gridline.app.ui.f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A target ellipse; draw it in one stroke (multiple laps are fine). */
class EllipseDrill(env: DrillEnv) : Drill(env) {
    override val type = "ellipses"
    private val pt = Paints(env.dp)
    private val guides = arrayOf("Axes", "Box", "Trace")
    private var guide = 0
    override val guideLabel: String get() = guides[guide]
    override fun cycleGuide() { guide = (guide + 1) % guides.size }

    private var target = Ellipse(0.0, 0.0, 1.0, 1.0, 0.0)
    private var pending: Stroke? = null
    private var last: Last? = null
    private val fadedGuide = pt.stroke(Colors.GUIDE_LIGHT, 1.2f * env.dp)

    private class Last(val target: Ellipse, val fit: Ellipse, val stroke: Stroke)

    override fun newRound() {
        val m = min(w, h).toDouble()
        val margin = m * 0.06
        repeat(100) {
            val rx = m * env.r(0.12, 0.32)
            val ratio = if (env.rnd.nextDouble() < 0.15) 1.0 else env.r(0.2, 0.9)
            val ry = rx * ratio
            val ang = env.r(0.0, PI)
            val ex = sqrt((rx * cos(ang)).let { it * it } + (ry * sin(ang)).let { it * it })
            val ey = sqrt((rx * sin(ang)).let { it * it } + (ry * cos(ang)).let { it * it })
            if (2 * (ex + margin) < w && 2 * (ey + margin) < h) {
                val cx = env.r(margin + ex, w - margin - ex)
                val cy = env.r(margin + ey, h - margin - ey)
                target = Ellipse.of(cx, cy, rx, ry, ang)
                return
            }
        }
        target = Ellipse.of(w / 2.0, h / 2.0, m * 0.2, m * 0.1, 0.0)
    }

    override fun addStroke(s: Stroke): Boolean {
        if (s.count < 12 || s.pathLength() < env.px(20f)) {
            feedback = "Too short, not counted."
            return false
        }
        pending = s
        return true
    }

    override fun score(): Attempt? {
        val s = pending ?: return null
        pending = null
        val xs = s.xd()
        val ys = s.yd()
        val fit = fitEllipse(xs, ys)
        if (fit == null || fit.rx > max(w, h) * 2 || fit.ry < env.px(1f)) {
            feedback = "Couldn't read that as an ellipse, so it's not counted."
            return null
        }
        var ss = 0.0
        for (i in xs.indices) { val e = fit.radialError(xs[i], ys[i]); ss += e * e }
        val shape = env.mm(sqrt(ss / xs.size))
        val t = target
        val size = ((fit.rx + fit.ry) / (t.rx + t.ry) - 1) * 100
        val ratioDiff = fit.ratio - t.ratio
        // Tilt only means something when neither ellipse is close to a circle.
        val tilt = if (t.ratio < 0.85 && fit.ratio < 0.92) abs(axisDiffDeg(fit.angle, t.angle)) else Double.NaN
        val center = env.mm(Math.hypot(fit.cx - t.cx, fit.cy - t.cy))
        val swept = fit.sweptDeg(xs, ys)

        val closure = when {
            swept < 330 -> "open (${f(360 - swept, 0)}° gap)"
            swept > 420 -> "${f(swept / 360, 1)} laps"
            else -> "closed"
        }
        val tip = when {
            shape > 1.0 && shape > env.mm(fit.ry) * 0.05 ->
                "Uneven curve. Ghost a few laps above the screen, then draw two laps without stopping."
            abs(ratioDiff) > 0.08 && ratioDiff > 0 -> "Too round for this one. Squash it more."
            abs(ratioDiff) > 0.08 -> "Too thin for this one. Open it up."
            !tilt.isNaN() && tilt > 5 -> "The long axis is rotated ${f(tilt, 0)}° from the target."
            abs(size) > 12 -> if (size > 0) "Too big. Watch the guide's extremes." else "Too small. Reach the guide's extremes."
            swept < 330 -> "Close the loop. Overshooting into a second lap is fine."
            else -> "Clean."
        }
        val tiltText = if (tilt.isNaN()) "" else " · Tilt ${f(tilt, 1)}°"
        feedback = "Shape ${f(shape, 2)} mm · Size ${if (size >= 0) "+" else ""}${f(size, 0)}% · " +
            "Ratio ${f(fit.ratio, 2)} vs ${f(t.ratio, 2)}$tiltText · Center ${f(center, 1)} mm · $closure\n$tip"
        last = Last(t, fit, s)
        return Attempt(
            mapOf(
                "shape" to shape,
                "size" to abs(size),
                "ratio" to abs(ratioDiff),
                "tilt" to tilt,
                "center" to center,
            ),
        )
    }

    override fun discardLast() {
        last = null
        feedback = "Last attempt discarded."
    }

    override fun draw(c: Canvas) {
        last?.let { l ->
            oval(c, l.target, fadedGuide)
            oval(c, l.fit, pt.ideal)
            c.drawPath(pt.path(l.stroke), pt.inkFaded)
        }
        val t = target
        val cx = t.cx.toFloat()
        val cy = t.cy.toFloat()
        val rx = t.rx.toFloat()
        val ry = t.ry.toFloat()
        c.save()
        c.rotate(Math.toDegrees(t.angle).toFloat(), cx, cy)
        when (guide) {
            0 -> {
                c.drawLine(cx - rx, cy, cx + rx, cy, pt.guide)
                c.drawLine(cx, cy - ry, cx, cy + ry, pt.guide)
                val r = 3 * pt.dp
                c.drawCircle(cx - rx, cy, r, pt.fill)
                c.drawCircle(cx + rx, cy, r, pt.fill)
                c.drawCircle(cx, cy - ry, r, pt.fill)
                c.drawCircle(cx, cy + ry, r, pt.fill)
            }
            1 -> {
                c.drawRect(cx - rx, cy - ry, cx + rx, cy + ry, pt.guide)
                c.drawLine(cx - rx, cy, cx + rx, cy, pt.guideLight)
                c.drawLine(cx, cy - ry, cx, cy + ry, pt.guideLight)
            }
            else -> c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, pt.guide)
        }
        c.restore()
        val k = 4 * pt.dp
        c.drawLine(cx - k, cy, cx + k, cy, pt.target)
        c.drawLine(cx, cy - k, cx, cy + k, pt.target)
    }

    private fun oval(c: Canvas, e: Ellipse, p: Paint) {
        val cx = e.cx.toFloat()
        val cy = e.cy.toFloat()
        c.save()
        c.rotate(Math.toDegrees(e.angle).toFloat(), cx, cy)
        c.drawOval(cx - e.rx.toFloat(), cy - e.ry.toFloat(), cx + e.rx.toFloat(), cy + e.ry.toFloat(), p)
        c.restore()
    }
}
