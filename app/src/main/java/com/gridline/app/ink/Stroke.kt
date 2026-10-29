package com.gridline.app.ink

import kotlin.math.hypot

/** One pen-down to pen-up path. Coordinates are in whatever space the owner uses. */
class Stroke(
    val color: Int,
    val size: Float,
    val erase: Boolean = false,
    val clearAll: Boolean = false,
) {
    var xs = FloatArray(64); private set
    var ys = FloatArray(64); private set
    var ps = FloatArray(64); private set
    private var ts = LongArray(64)
    var count = 0; private set

    fun add(x: Float, y: Float, pressure: Float, timeMs: Long) {
        if (count > 0 && xs[count - 1] == x && ys[count - 1] == y) {
            if (pressure > ps[count - 1]) ps[count - 1] = pressure
            return
        }
        if (count == xs.size) {
            val n = count * 2
            xs = xs.copyOf(n); ys = ys.copyOf(n); ps = ps.copyOf(n); ts = ts.copyOf(n)
        }
        xs[count] = x; ys[count] = y; ps[count] = pressure; ts[count] = timeMs
        count++
    }

    val durationMs: Long get() = if (count < 2) 0 else ts[count - 1] - ts[0]

    fun pathLength(): Float {
        var l = 0f
        for (i in 1 until count) l += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        return l
    }

    fun xd() = DoubleArray(count) { xs[it].toDouble() }
    fun yd() = DoubleArray(count) { ys[it].toDouble() }
}
