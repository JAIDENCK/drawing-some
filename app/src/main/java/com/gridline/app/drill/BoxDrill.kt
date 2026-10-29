package com.gridline.app.drill

import android.graphics.Canvas
import com.gridline.app.geom.LineShape
import com.gridline.app.geom.V
import com.gridline.app.geom.analyzeLine
import com.gridline.app.geom.clusterDirections
import com.gridline.app.geom.leastSquaresPoint
import com.gridline.app.geom.lineAngleDeg
import com.gridline.app.geom.meanDirection
import com.gridline.app.ink.Stroke
import com.gridline.app.ui.Colors
import com.gridline.app.ui.f
import kotlin.math.abs
import kotlin.math.min

/**
 * Boxes in perspective. Every edge you draw is extended across the page after
 * Check, so you can see where it actually goes compared to where it should go.
 *
 *  TWO:  horizon, two vanishing points (often off-screen), a given front edge
 *        and the start of the two top edges.
 *  ONE:  horizon, one vanishing point and a given front face.
 *  FREE: nothing given. Edges are grouped by direction and each group is
 *        checked against its own best-fit vanishing point.
 */
class BoxDrill(env: DrillEnv, private val mode: Mode) : Drill(env) {
    enum class Mode { ONE, TWO, FREE }

    private enum class Conv { NONE, FAST, SLOW, WRONG }

    private class Edge(
        val stroke: Stroke,
        val shape: LineShape,
        val group: Int,
        val target: V?,
        val err: Double,
        val conv: Conv,
    )

    override val type = when (mode) {
        Mode.ONE -> "box1"
        Mode.TWO -> "box2"
        Mode.FREE -> "boxfree"
    }
    override val multiStroke: Boolean get() = true

    private val pt = Paints(env.dp)
    private val dp = env.dp
    private val guides = when (mode) {
        Mode.TWO -> arrayOf("Rays", "Horizon", "Stubs only")
        Mode.ONE -> arrayOf("Rays", "VP only")
        Mode.FREE -> emptyArray()
    }
    private var guide = 0
    override val guideLabel: String? get() = if (guides.isEmpty()) null else guides[guide]
    override fun cycleGuide() {
        if (guides.isNotEmpty()) guide = (guide + 1) % guides.size
    }

    private var horizon = 0.0
    private var vps: List<V> = emptyList()
    private val given = ArrayList<Pair<V, V>>()
    private val rayTargets = ArrayList<V>()
    private val strokes = ArrayList<Stroke>()
    private var edges: List<Edge>? = null
    private var freeVps: Array<V?> = arrayOfNulls(3)
    override val checked: Boolean get() = edges != null

    private val extPaint = pt.stroke(Colors.GUIDE, 1f * dp)
    private val horizonPaint = pt.stroke(Colors.GUIDE, 1.4f * dp)
    private val vpPaint = pt.stroke(Colors.INK, 1.6f * dp)

    // ---- rounds ---------------------------------------------------------

    override fun newRound() {
        strokes.clear()
        edges = null
        given.clear()
        rayTargets.clear()
        freeVps = arrayOfNulls(3)
        when (mode) {
            Mode.TWO -> roundTwo()
            Mode.ONE -> roundOne()
            Mode.FREE -> vps = emptyList()
        }
        feedback = when (mode) {
            Mode.TWO -> "The front vertical edge and the start of both top edges are given. " +
                "Draw the rest of the box, then Check."
            Mode.ONE -> "Pull the front face back toward the vanishing point and draw the back face. Then Check."
            Mode.FREE -> "Draw a box in any perspective, at least 3 lines per direction. Then Check."
        }
    }

    private fun roundTwo() {
        horizon = h * env.r(0.3, 0.7)
        val left = V(w * env.r(-0.5, 0.1), horizon)
        val right = V(w * env.r(0.9, 1.5), horizon)
        vps = listOf(left, right)
        val fx = clamp(left.x + (right.x - left.x) * env.r(0.35, 0.65), w * 0.3, w * 0.7)
        val eh = h * env.r(0.18, 0.3)
        var top = when (env.rnd.nextInt(3)) {
            0 -> horizon + h * env.r(0.04, 0.12) // below eye level: you see the top
            1 -> horizon - h * env.r(0.04, 0.12) - eh // above eye level: you see the bottom
            else -> horizon - eh * env.r(0.3, 0.7) // eye level cuts through the box
        }
        top = clamp(top, h * 0.1, h * 0.9 - eh)
        val t = V(fx, top)
        val b = V(fx, top + eh)
        given.add(t to b)
        val stub = min(w, h) * 0.07
        for (vp in vps) given.add(t to (t + (vp - t).unit() * stub))
        rayTargets.add(t)
        rayTargets.add(b)
    }

    private fun roundOne() {
        horizon = h * env.r(0.35, 0.65)
        val vp = V(w * env.r(0.35, 0.65), horizon)
        vps = listOf(vp)
        val s = min(w, h) * env.r(0.16, 0.26)
        val fw = s * env.r(0.8, 1.3)
        val sx = if (env.rnd.nextBoolean()) 1 else -1
        val sy = if (env.rnd.nextBoolean()) 1 else -1
        val cx = clamp(vp.x + sx * w * env.r(0.18, 0.3), fw / 2 + w * 0.05, w - fw / 2 - w * 0.05)
        val cy = clamp(vp.y + sy * h * env.r(0.12, 0.25), s / 2 + h * 0.05, h - s / 2 - h * 0.05)
        val tl = V(cx - fw / 2, cy - s / 2)
        val tr = V(cx + fw / 2, cy - s / 2)
        val br = V(cx + fw / 2, cy + s / 2)
        val bl = V(cx - fw / 2, cy + s / 2)
        given.add(tl to tr); given.add(tr to br); given.add(br to bl); given.add(bl to tl)
        rayTargets.addAll(listOf(tl, tr, br, bl))
    }

    private fun clamp(v: Double, lo: Double, hi: Double) = if (lo > hi) (lo + hi) / 2 else v.coerceIn(lo, hi)

    // ---- strokes --------------------------------------------------------

    override fun addStroke(s: Stroke): Boolean {
        if (checked) return false
        if (s.count >= 2 && s.pathLength() >= env.px(4f)) strokes.add(s)
        return false
    }

    override fun undoStroke(): Boolean {
        if (checked || strokes.isEmpty()) return false
        strokes.removeAt(strokes.size - 1)
        return true
    }

    // ---- scoring --------------------------------------------------------

    override fun score(): Attempt? {
        if (checked) return null
        if (strokes.isEmpty()) {
            feedback = "Draw the edges first, then Check."
            return null
        }
        val shapes = strokes.map { analyzeLine(it.xd(), it.yd()) }
        val result = if (mode == Mode.FREE) classifyFree(shapes) else classifyKnown(shapes)
        val counted = result.filter { !it.err.isNaN() }
        if (counted.isEmpty()) {
            feedback = "Not enough to check yet: each direction needs at least 3 lines " +
                "(any two lines meet somewhere, so two can't be wrong)."
            return null
        }
        edges = result
        val conv = counted.map { abs(it.err) }.average()
        val worst = counted.maxOf { abs(it.err) }
        val wobble = shapes.map { env.mm(it.wobble) }.average()
        feedback = describe(result, wobble)
        return Attempt(mapOf("conv" to conv, "worst" to worst, "wobble" to wobble))
    }

    private fun classifyKnown(shapes: List<LineShape>): List<Edge> = shapes.mapIndexed { i, sh ->
        val mid = sh.fit.center
        val cands = ArrayList<Triple<Int, V, V?>>()
        cands.add(Triple(0, V(0.0, 1.0), null))
        if (mode == Mode.TWO) {
            cands.add(Triple(1, vps[0] - mid, vps[0]))
            cands.add(Triple(2, vps[1] - mid, vps[1]))
        } else {
            cands.add(Triple(1, V(1.0, 0.0), null))
            cands.add(Triple(2, vps[0] - mid, vps[0]))
        }
        val best = cands.minBy { abs(lineAngleDeg(it.second, sh.dir)) }
        val err = lineAngleDeg(best.second, sh.dir)
        val conv = best.third?.let { convergence(mid, sh.dir, it, err) } ?: Conv.NONE
        Edge(strokes[i], sh, best.first, best.second, err, conv)
    }

    private fun classifyFree(shapes: List<LineShape>): List<Edge> {
        val labels = clusterDirections(shapes.map { it.dir }, 3)
        val members = Array(3) { g -> shapes.indices.filter { labels[it] == g } }
        val means = Array(3) { g -> meanDirection(members[g].map { shapes[it].dir }) }
        for (g in 0 until 3) {
            freeVps[g] = if (members[g].size >= 3) {
                leastSquaresPoint(members[g].map { shapes[it].fit.center to shapes[it].dir })
            } else null
        }
        return shapes.mapIndexed { i, sh ->
            val g = labels[i]
            if (members[g].size < 3) {
                Edge(strokes[i], sh, g, null, Double.NaN, Conv.NONE)
            } else {
                val vp = freeVps[g]
                val target = if (vp != null) vp - sh.fit.center else means[g]
                Edge(strokes[i], sh, g, target, lineAngleDeg(target, sh.dir), Conv.NONE)
            }
        }
    }

    /** Where does this line, extended, hit the horizon compared to the vanishing point? */
    private fun convergence(mid: V, dir: V, vp: V, err: Double): Conv {
        if (abs(err) < 1.0) return Conv.NONE
        if (abs(mid.y - horizon) < h * 0.03) return Conv.NONE
        if (abs(dir.y) < 1e-6) return Conv.SLOW
        val ix = mid.x + (horizon - mid.y) / dir.y * dir.x
        val dVp = vp.x - mid.x
        val dI = ix - mid.x
        return when {
            dI * dVp < 0 -> Conv.WRONG
            abs(dI) < abs(dVp) -> Conv.FAST
            else -> Conv.SLOW
        }
    }

    private fun groupName(g: Int): String = when (mode) {
        Mode.TWO -> arrayOf("Verticals", "Lines to the left VP", "Lines to the right VP")[g]
        Mode.ONE -> arrayOf("Verticals", "Horizontals", "Lines to the VP")[g]
        Mode.FREE -> arrayOf("Set A", "Set B", "Set C")[g]
    }

    private fun groupColor(g: Int): Int = when (mode) {
        Mode.TWO -> intArrayOf(Colors.SET_GREY, Colors.SET_BLUE, Colors.SET_RUST)[g]
        Mode.ONE -> intArrayOf(Colors.SET_GREY, Colors.SET_PURPLE, Colors.SET_BLUE)[g]
        Mode.FREE -> intArrayOf(Colors.SET_BLUE, Colors.SET_RUST, Colors.SET_PURPLE)[g]
    }

    private fun isVpGroup(g: Int) = when (mode) {
        Mode.TWO -> g == 1 || g == 2
        Mode.ONE -> g == 2
        Mode.FREE -> false
    }

    private fun describe(edges: List<Edge>, wobble: Double): String {
        val sb = StringBuilder()
        var worstG = -1
        var worstAvg = 0.0
        for (g in 0 until 3) {
            val es = edges.filter { it.group == g }
            if (es.isEmpty()) continue
            val c = es.filter { !it.err.isNaN() }
            sb.append(groupName(g)).append(": ").append(es.size)
            if (c.isEmpty()) {
                sb.append(" (too few to check, need 3)")
            } else {
                val avg = c.map { abs(it.err) }.average()
                val worst = c.maxOf { abs(it.err) }
                sb.append(" · avg ${f(avg, 1)}° · worst ${f(worst, 1)}°")
                val fast = c.count { it.conv == Conv.FAST }
                val slow = c.count { it.conv == Conv.SLOW }
                val wrong = c.count { it.conv == Conv.WRONG }
                val parts = ArrayList<String>()
                if (fast > 0) parts.add("$fast converge too fast")
                if (slow > 0) parts.add("$slow too parallel")
                if (wrong > 0) parts.add("$wrong point away")
                if (parts.isNotEmpty()) sb.append(" · ").append(parts.joinToString(", "))
                if (avg > worstAvg) { worstAvg = avg; worstG = g }
            }
            sb.append('\n')
        }
        sb.append(tip(edges, worstG, worstAvg, wobble))
        return sb.toString()
    }

    private fun tip(edges: List<Edge>, g: Int, avg: Double, wobble: Double): String {
        val shaky = wobble > 0.5
        if (g < 0 || avg < 1.5) {
            return if (shaky) {
                "Directions are good; the lines are shaky (${f(wobble, 2)} mm). One confident stroke per edge."
            } else {
                "Tight. Next time make the box bigger or turn it further."
            }
        }
        val name = groupName(g)
        val main = when {
            mode == Mode.FREE ->
                "Lines in $name don't agree on a vanishing point. Pick one spot and ghost every line in that set toward it."
            isVpGroup(g) -> {
                val c = edges.filter { it.group == g }
                val fast = c.count { it.conv == Conv.FAST }
                val slow = c.count { it.conv == Conv.SLOW }
                val wrong = c.count { it.conv == Conv.WRONG }
                when {
                    wrong >= fast && wrong >= slow && wrong > 0 ->
                        "$name spread apart instead of converging. They should all narrow toward the VP."
                    fast >= slow && fast > 0 ->
                        "$name converge too fast; they'd meet before the VP. Aim further out along the horizon."
                    slow > 0 ->
                        "$name are too parallel; they'd meet past the VP. Angle them more toward it."
                    else -> "$name are off by ${f(avg, 1)}° on average. Ghost each one toward the VP first."
                }
            }
            g == 0 -> "Verticals lean. Keep them parallel to the side of the screen."
            else -> "Front-face horizontals tilt. Keep them level with the bottom of the screen."
        }
        return if (shaky) "$main Lines are also shaky (${f(wobble, 2)} mm)." else main
    }

    // ---- drawing --------------------------------------------------------

    override fun draw(c: Canvas) {
        val chk = edges
        val showHorizon = mode != Mode.FREE && (chk != null || mode == Mode.ONE || guide < 2)
        if (showHorizon) {
            c.drawLine(-w, horizon.toFloat(), 2 * w, horizon.toFloat(), horizonPaint)
            vps.forEach { drawVp(c, it, Colors.INK) }
        }
        if (mode != Mode.FREE && chk == null && guide == 0) {
            val far = (w + h) * 3.0
            for (vp in vps) for (t in rayTargets) {
                val end = vp + (t - vp).unit() * far
                c.drawLine(vp.x.toFloat(), vp.y.toFloat(), end.x.toFloat(), end.y.toFloat(), pt.guideLight)
            }
        }
        for ((a, b) in given) {
            c.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), pt.given)
        }
        if (chk == null) {
            for (s in strokes) c.drawPath(pt.path(s), pt.ink)
            return
        }
        for (e in chk) drawExtension(c, e)
        for (e in chk) drawEdge(c, e)
        if (mode == Mode.FREE) {
            for (g in 0 until 3) freeVps[g]?.let { drawVp(c, it, groupColor(g)) }
        }
    }

    private fun drawExtension(c: Canvas, e: Edge) {
        val sh = e.shape
        val far = (w + h) * 3.0
        val a = sh.fit.center - sh.dir * far
        val b = sh.fit.center + sh.dir * far
        extPaint.color = Colors.withAlpha(groupColor(e.group), 110)
        c.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), extPaint)
    }

    private fun drawEdge(c: Canvas, e: Edge) {
        val sh = e.shape
        c.drawPath(pt.path(e.stroke), pt.ink)
        if (e.err.isNaN()) return
        val t = e.target
        if (abs(e.err) > 1.0 && t != null) {
            // Where this edge should have gone: same start, same length, correct direction.
            var dir = t.unit()
            if ((dir dot (sh.end - sh.start)) < 0) dir = dir * -1.0
            val end = sh.start + dir * sh.length
            c.drawLine(sh.start.x.toFloat(), sh.start.y.toFloat(), end.x.toFloat(), end.y.toFloat(), pt.idealDashed)
        }
        val n = sh.dir.perp()
        val m = sh.fit.center + n * (14.0 * dp)
        val label = "${f(abs(e.err), 1)}°"
        val x = m.x.toFloat() - pt.text.measureText(label) / 2
        pt.label(c, label, x, m.y.toFloat() + 4 * dp, Colors.severity(abs(e.err)))
    }

    private fun drawVp(c: Canvas, vp: V, color: Int) {
        val x = vp.x.toFloat()
        val y = vp.y.toFloat()
        val k = 6 * dp
        if (x in 0f..w && y in 0f..h) {
            vpPaint.color = color
            c.drawLine(x - k, y - k, x + k, y + k, vpPaint)
            c.drawLine(x - k, y + k, x + k, y - k, vpPaint)
            pt.label(c, "VP", x + k + 2 * dp, y - k, color)
            return
        }
        val m = 10 * dp
        val label = when {
            x < 0 -> "← VP"
            x > w -> "VP →"
            y < 0 -> "↑ VP"
            else -> "↓ VP"
        }
        val tw = pt.text.measureText(label)
        val lx = (if (x < 0) m else if (x > w) w - m - tw else x - tw / 2).coerceIn(m, w - m - tw)
        val ly = y.coerceIn(m + 13 * dp, h - m) - 4 * dp
        pt.label(c, label, lx, ly, color)
    }
}
