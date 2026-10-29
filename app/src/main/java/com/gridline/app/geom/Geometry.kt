package com.gridline.app.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** 2D vector in doubles. All fitting is done in doubles to stay stable. */
data class V(val x: Double, val y: Double) {
    operator fun plus(o: V) = V(x + o.x, y + o.y)
    operator fun minus(o: V) = V(x - o.x, y - o.y)
    operator fun times(s: Double) = V(x * s, y * s)
    infix fun dot(o: V) = x * o.x + y * o.y
    infix fun cross(o: V) = x * o.y - y * o.x
    val len: Double get() = hypot(x, y)
    fun unit(): V {
        val l = len
        return if (l < 1e-12) V(1.0, 0.0) else V(x / l, y / l)
    }
    fun perp() = V(-y, x)
}

/** Total-least-squares line: passes through [center], runs along unit [dir]. */
class LineFit(val center: V, val dir: V, val rms: Double)

fun fitLine(xs: DoubleArray, ys: DoubleArray): LineFit {
    val n = xs.size
    var mx = 0.0
    var my = 0.0
    for (i in 0 until n) { mx += xs[i]; my += ys[i] }
    mx /= n; my /= n
    var sxx = 0.0
    var sxy = 0.0
    var syy = 0.0
    for (i in 0 until n) {
        val dx = xs[i] - mx
        val dy = ys[i] - my
        sxx += dx * dx; sxy += dx * dy; syy += dy * dy
    }
    val th = 0.5 * atan2(2 * sxy, sxx - syy)
    val dir = V(cos(th), sin(th))
    val nrm = dir.perp()
    var ss = 0.0
    for (i in 0 until n) {
        val d = (xs[i] - mx) * nrm.x + (ys[i] - my) * nrm.y
        ss += d * d
    }
    return LineFit(V(mx, my), dir, sqrt(ss / n))
}

/**
 * Signed angle (degrees) from line [from] to line [to], treating both as
 * undirected lines, so the result is in (-90, 90].
 */
fun lineAngleDeg(from: V, to: V): Double {
    var d = atan2(from cross to, from dot to)
    if (d > PI / 2) d -= PI
    if (d <= -PI / 2) d += PI
    return Math.toDegrees(d)
}

/**
 * How straight a stroke is.
 *  - [wobble]: RMS of the stroke around a smooth quadratic through it (shake).
 *  - [bow]: biggest sideways bulge of that quadratic from the start-end chord (curve).
 * Separating the two matters: shaky lines and curved lines have different fixes.
 */
class LineShape(
    val start: V,
    val end: V,
    val length: Double,
    val wobble: Double,
    val bow: Double,
    val fit: LineFit,
) {
    val dir: V get() = fit.dir
}

fun analyzeLine(xs: DoubleArray, ys: DoubleArray): LineShape {
    val n = xs.size
    val s = V(xs[0], ys[0])
    val e = V(xs[n - 1], ys[n - 1])
    var fit = fitLine(xs, ys)
    if ((fit.dir dot (e - s)) < 0) fit = LineFit(fit.center, fit.dir * -1.0, fit.rms)
    val chord = e - s
    val len = chord.len
    if (len < 1e-6 || n < 4) return LineShape(s, e, len, fit.rms, 0.0, fit)

    val u = chord.unit()
    val v = u.perp()
    val mid = (s + e) * 0.5
    val half = len / 2
    val us = DoubleArray(n)
    val vs = DoubleArray(n)
    var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
    var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
    for (i in 0 until n) {
        val p = V(xs[i], ys[i])
        val uu = ((p - mid) dot u) / half
        val vv = (p - s) dot v
        us[i] = uu; vs[i] = vv
        val u2 = uu * uu
        s0 += 1.0; s1 += uu; s2 += u2; s3 += u2 * uu; s4 += u2 * u2
        t0 += vv; t1 += uu * vv; t2 += u2 * vv
    }
    val coef = solve3(
        arrayOf(
            doubleArrayOf(s4, s3, s2),
            doubleArrayOf(s3, s2, s1),
            doubleArrayOf(s2, s1, s0),
        ),
        doubleArrayOf(t2, t1, t0),
    ) ?: return LineShape(s, e, len, fit.rms, 0.0, fit)
    val (a, b, c) = Triple(coef[0], coef[1], coef[2])
    var ss = 0.0
    for (i in 0 until n) {
        val r = vs[i] - (a * us[i] * us[i] + b * us[i] + c)
        ss += r * r
    }
    var bow = 0.0
    for (k in 0..20) {
        val uu = -1.0 + k * 0.1
        val q = a * uu * uu + b * uu + c
        if (abs(q) > abs(bow)) bow = q
    }
    return LineShape(s, e, len, sqrt(ss / n), bow, fit)
}

/** Point closest (least squares) to every line. Null when the lines are ~parallel. */
fun leastSquaresPoint(lines: List<Pair<V, V>>): V? {
    var a11 = 0.0; var a12 = 0.0; var a22 = 0.0; var b1 = 0.0; var b2 = 0.0
    for ((p, d) in lines) {
        val n = d.unit().perp()
        val k = n dot p
        a11 += n.x * n.x; a12 += n.x * n.y; a22 += n.y * n.y
        b1 += n.x * k; b2 += n.y * k
    }
    val det = a11 * a22 - a12 * a12
    val tr = a11 + a22
    if (tr <= 0 || det < 1e-4 * tr * tr) return null
    return V((b1 * a22 - b2 * a12) / det, (a11 * b2 - a12 * b1) / det)
}

/** Average direction of undirected lines (doubled-angle mean). */
fun meanDirection(dirs: List<V>): V {
    var sx = 0.0
    var sy = 0.0
    for (d in dirs) {
        val a = 2 * atan2(d.y, d.x)
        sx += cos(a); sy += sin(a)
    }
    val a = atan2(sy, sx) / 2
    return V(cos(a), sin(a))
}

/** Groups undirected line directions into up to [k] sets (k-means on doubled angles). */
fun clusterDirections(dirs: List<V>, k: Int): IntArray {
    val n = dirs.size
    if (n == 0) return IntArray(0)
    val kk = minOf(k, n)
    val pts = dirs.map { val a = 2 * atan2(it.y, it.x); V(cos(a), sin(a)) }
    val cent = ArrayList<V>()
    cent.add(pts[0])
    while (cent.size < kk) {
        var best = 0
        var bestD = -1.0
        for (i in pts.indices) {
            val d = cent.minOf { c -> (pts[i] - c).len }
            if (d > bestD) { bestD = d; best = i }
        }
        cent.add(pts[best])
    }
    val lab = IntArray(n)
    repeat(25) {
        for (i in 0 until n) {
            var bj = 0
            var bd = Double.MAX_VALUE
            for (j in cent.indices) {
                val d = (pts[i] - cent[j]).len
                if (d < bd) { bd = d; bj = j }
            }
            lab[i] = bj
        }
        for (j in cent.indices) {
            var sx = 0.0; var sy = 0.0; var c = 0
            for (i in 0 until n) if (lab[i] == j) { sx += pts[i].x; sy += pts[i].y; c++ }
            if (c > 0) cent[j] = V(sx / c, sy / c)
        }
    }
    return lab
}

/** Ellipse with semi-axes [rx] >= [ry]; [angle] (radians, 0..PI) is the direction of rx. */
class Ellipse(val cx: Double, val cy: Double, val rx: Double, val ry: Double, val angle: Double) {
    val ratio: Double get() = ry / rx

    fun local(px: Double, py: Double): V {
        val dx = px - cx
        val dy = py - cy
        val c = cos(angle)
        val s = sin(angle)
        return V(dx * c + dy * s, -dx * s + dy * c)
    }

    /** Approximate signed distance from the curve along the ray from the center. */
    fun radialError(px: Double, py: Double): Double {
        val p = local(px, py)
        val r = p.len
        if (r < 1e-9) return -ry
        val phi = atan2(p.y, p.x)
        val a = ry * cos(phi)
        val b = rx * sin(phi)
        val re = rx * ry / sqrt(a * a + b * b)
        return r - re
    }

    /** Total angle a stroke travels around this ellipse, in degrees (360 = one lap). */
    fun sweptDeg(xs: DoubleArray, ys: DoubleArray): Double {
        var total = 0.0
        var prev = Double.NaN
        for (i in xs.indices) {
            val p = local(xs[i], ys[i])
            val a = atan2(p.y / ry, p.x / rx)
            if (!prev.isNaN()) {
                var d = a - prev
                while (d > PI) d -= 2 * PI
                while (d < -PI) d += 2 * PI
                total += d
            }
            prev = a
        }
        return abs(Math.toDegrees(total))
    }

    companion object {
        fun of(cx: Double, cy: Double, a: Double, b: Double, ang: Double): Ellipse {
            var rx = a
            var ry = b
            var t = ang
            if (ry > rx) { val tmp = rx; rx = ry; ry = tmp; t += PI / 2 }
            t = ((t % PI) + PI) % PI
            return Ellipse(cx, cy, rx, ry, t)
        }
    }
}

/** Difference of two axis directions (radians) as degrees in (-90, 90]. */
fun axisDiffDeg(a: Double, b: Double): Double {
    var d = Math.toDegrees(a - b) % 180.0
    if (d > 90) d -= 180
    if (d <= -90) d += 180
    return d
}

/**
 * Direct least-squares ellipse fit (Fitzgibbon; numerically stable form by
 * Halir & Flusser). Always returns an ellipse (never a hyperbola) or null.
 */
fun fitEllipse(xs: DoubleArray, ys: DoubleArray): Ellipse? {
    val n = xs.size
    if (n < 6) return null
    var mx = 0.0
    var my = 0.0
    for (i in 0 until n) { mx += xs[i]; my += ys[i] }
    mx /= n; my /= n
    var sc = 0.0
    for (i in 0 until n) { val dx = xs[i] - mx; val dy = ys[i] - my; sc += dx * dx + dy * dy }
    sc = sqrt(sc / n)
    if (sc < 1e-9) return null

    val s1 = Array(3) { DoubleArray(3) }
    val s2 = Array(3) { DoubleArray(3) }
    val s3 = Array(3) { DoubleArray(3) }
    val d1 = DoubleArray(3)
    val d2 = DoubleArray(3)
    for (i in 0 until n) {
        val x = (xs[i] - mx) / sc
        val y = (ys[i] - my) / sc
        d1[0] = x * x; d1[1] = x * y; d1[2] = y * y
        d2[0] = x; d2[1] = y; d2[2] = 1.0
        for (a in 0..2) for (b in 0..2) {
            s1[a][b] += d1[a] * d1[b]
            s2[a][b] += d1[a] * d2[b]
            s3[a][b] += d2[a] * d2[b]
        }
    }
    val s3i = inv3(s3) ?: return null
    // T = -S3^-1 * S2^T
    val t = Array(3) { r ->
        DoubleArray(3) { c ->
            var acc = 0.0
            for (k in 0..2) acc += s3i[r][k] * s2[c][k]
            -acc
        }
    }
    // M = S1 + S2 * T, then premultiply by inverse of the constraint matrix
    val m = Array(3) { r ->
        DoubleArray(3) { c ->
            var acc = s1[r][c]
            for (k in 0..2) acc += s2[r][k] * t[k][c]
            acc
        }
    }
    val mp = arrayOf(
        DoubleArray(3) { m[2][it] / 2 },
        DoubleArray(3) { -m[1][it] },
        DoubleArray(3) { m[0][it] / 2 },
    )
    val tr = mp[0][0] + mp[1][1] + mp[2][2]
    val minors = (mp[0][0] * mp[1][1] - mp[0][1] * mp[1][0]) +
        (mp[0][0] * mp[2][2] - mp[0][2] * mp[2][0]) +
        (mp[1][1] * mp[2][2] - mp[1][2] * mp[2][1])
    val det = det3(mp)
    val roots = realCubicRoots(-tr, minors, -det)

    var best: DoubleArray? = null
    var bestCond = 0.0
    for (lam in roots) {
        val a = Array(3) { r -> DoubleArray(3) { c -> mp[r][c] - if (r == c) lam else 0.0 } }
        val v = nullVector(a) ?: continue
        val cond = 4 * v[0] * v[2] - v[1] * v[1]
        if (cond > bestCond) { bestCond = cond; best = v }
    }
    val a1 = best ?: return null
    val a2 = DoubleArray(3) { r -> t[r][0] * a1[0] + t[r][1] * a1[1] + t[r][2] * a1[2] }
    val e = conicToEllipse(a1[0], a1[1], a1[2], a2[0], a2[1], a2[2]) ?: return null
    return Ellipse.of(e.cx * sc + mx, e.cy * sc + my, e.rx * sc, e.ry * sc, e.angle)
}

private fun conicToEllipse(a: Double, b: Double, c: Double, d: Double, e: Double, f: Double): Ellipse? {
    val den = b * b - 4 * a * c
    if (den >= 0) return null
    val x0 = (2 * c * d - b * e) / den
    val y0 = (2 * a * e - b * d) / den
    val fc = a * x0 * x0 + b * x0 * y0 + c * y0 * y0 + d * x0 + e * y0 + f
    val th = 0.5 * atan2(b, a - c)
    val co = cos(th)
    val si = sin(th)
    val l1 = a * co * co + b * co * si + c * si * si
    val l2 = a * si * si - b * co * si + c * co * co
    val r1 = -fc / l1
    val r2 = -fc / l2
    if (r1 <= 0 || r2 <= 0) return null
    return Ellipse.of(x0, y0, sqrt(r1), sqrt(r2), th)
}

/** Real roots of x^3 + a x^2 + b x + c. */
fun realCubicRoots(a: Double, b: Double, c: Double): DoubleArray {
    val p = b - a * a / 3
    val q = 2 * a * a * a / 27 - a * b / 3 + c
    val off = -a / 3
    val disc = q * q / 4 + p * p * p / 27
    val raw = when {
        disc > 0 -> {
            val s = sqrt(disc)
            doubleArrayOf(cbrt(-q / 2 + s) + cbrt(-q / 2 - s) + off)
        }
        p == 0.0 -> doubleArrayOf(off)
        else -> {
            val r = sqrt(-p / 3)
            val arg = ((-q / 2) / (r * r * r)).coerceIn(-1.0, 1.0)
            val phi = acos(arg)
            DoubleArray(3) { k -> 2 * r * cos((phi - 2 * PI * k) / 3) + off }
        }
    }
    // A couple of Newton steps to clean up rounding.
    for (i in raw.indices) {
        var x = raw[i]
        repeat(3) {
            val fx = ((x + a) * x + b) * x + c
            val dfx = (3 * x + 2 * a) * x + b
            if (abs(dfx) > 1e-15) x -= fx / dfx
        }
        raw[i] = x
    }
    return raw
}

private fun nullVector(m: Array<DoubleArray>): DoubleArray? {
    fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )
    val cands = listOf(cross(m[0], m[1]), cross(m[0], m[2]), cross(m[1], m[2]))
    val v = cands.maxByOrNull { it[0] * it[0] + it[1] * it[1] + it[2] * it[2] } ?: return null
    val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    if (l < 1e-14) return null
    return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l)
}

fun det3(m: Array<DoubleArray>): Double =
    m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
        m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
        m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])

fun inv3(m: Array<DoubleArray>): Array<DoubleArray>? {
    val d = det3(m)
    if (abs(d) < 1e-18) return null
    val r = Array(3) { DoubleArray(3) }
    r[0][0] = (m[1][1] * m[2][2] - m[1][2] * m[2][1]) / d
    r[0][1] = (m[0][2] * m[2][1] - m[0][1] * m[2][2]) / d
    r[0][2] = (m[0][1] * m[1][2] - m[0][2] * m[1][1]) / d
    r[1][0] = (m[1][2] * m[2][0] - m[1][0] * m[2][2]) / d
    r[1][1] = (m[0][0] * m[2][2] - m[0][2] * m[2][0]) / d
    r[1][2] = (m[0][2] * m[1][0] - m[0][0] * m[1][2]) / d
    r[2][0] = (m[1][0] * m[2][1] - m[1][1] * m[2][0]) / d
    r[2][1] = (m[0][1] * m[2][0] - m[0][0] * m[2][1]) / d
    r[2][2] = (m[0][0] * m[1][1] - m[0][1] * m[1][0]) / d
    return r
}

/** Solves a 3x3 linear system with partial pivoting. */
fun solve3(m: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
    val a = Array(3) { r -> doubleArrayOf(m[r][0], m[r][1], m[r][2], b[r]) }
    for (col in 0 until 3) {
        var piv = col
        for (r in col + 1 until 3) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
        if (abs(a[piv][col]) < 1e-12) return null
        val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
        for (r in 0 until 3) if (r != col) {
            val f = a[r][col] / a[col][col]
            for (c in col until 4) a[r][c] -= f * a[col][c]
        }
    }
    return DoubleArray(3) { a[it][3] / a[it][it] }
}
