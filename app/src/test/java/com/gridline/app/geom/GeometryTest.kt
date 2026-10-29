package com.gridline.app.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {

    @Test
    fun straightLineHasNoWobbleOrBow() {
        val xs = DoubleArray(100) { 10.0 + it * 3.0 }
        val ys = DoubleArray(100) { 20.0 + it * 1.5 }
        val s = analyzeLine(xs, ys)
        assertEquals(0.0, s.wobble, 1e-6)
        assertEquals(0.0, s.bow, 1e-6)
    }

    @Test
    fun arcIsBowNotWobble() {
        // Shallow arc: 300 px long, bulging 12 px in the middle.
        val n = 200
        val xs = DoubleArray(n) { it * 300.0 / (n - 1) }
        val ys = DoubleArray(n) { val u = (it / (n - 1.0)) * 2 - 1; 12.0 * (1 - u * u) }
        val s = analyzeLine(xs, ys)
        assertEquals(12.0, abs(s.bow), 0.5)
        assertTrue("wobble ${s.wobble}", s.wobble < 0.2)
    }

    @Test
    fun noiseIsWobble() {
        val rnd = Random(1)
        val xs = DoubleArray(300) { it.toDouble() }
        val ys = DoubleArray(300) { rnd.nextGaussian() * 2.0 }
        val s = analyzeLine(xs, ys)
        assertEquals(2.0, s.wobble, 0.4)
    }

    @Test
    fun ellipseFitRecoversParameters() {
        val cx = 400.0; val cy = 300.0; val rx = 150.0; val ry = 60.0; val ang = 0.5
        val n = 120
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        for (i in 0 until n) {
            val t = 2 * PI * i / n
            val x = rx * cos(t); val y = ry * sin(t)
            xs[i] = cx + x * cos(ang) - y * sin(ang)
            ys[i] = cy + x * sin(ang) + y * cos(ang)
        }
        val e = fitEllipse(xs, ys)
        assertNotNull(e)
        e!!
        assertEquals(cx, e.cx, 1e-3)
        assertEquals(cy, e.cy, 1e-3)
        assertEquals(rx, e.rx, 1e-3)
        assertEquals(ry, e.ry, 1e-3)
        assertEquals(0.0, axisDiffDeg(e.angle, ang), 1e-3)
        assertEquals(360.0, e.sweptDeg(xs + xs[0], ys + ys[0]), 1.0)
        for (i in 0 until n) assertEquals(0.0, e.radialError(xs[i], ys[i]), 1e-3)
    }

    @Test
    fun ellipseFitOnPartialNoisyArc() {
        val rnd = Random(7)
        val n = 80
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        for (i in 0 until n) {
            val t = 1.6 * PI * i / n
            xs[i] = 100 + 80 * cos(t) + rnd.nextGaussian()
            ys[i] = 100 + 50 * sin(t) + rnd.nextGaussian()
        }
        val e = fitEllipse(xs, ys)!!
        assertEquals(80.0, e.rx, 4.0)
        assertEquals(50.0, e.ry, 4.0)
    }

    @Test
    fun circleFits() {
        val n = 60
        val xs = DoubleArray(n) { 50 + 40 * cos(2 * PI * it / n) }
        val ys = DoubleArray(n) { 70 + 40 * sin(2 * PI * it / n) }
        val e = fitEllipse(xs, ys)!!
        assertEquals(40.0, e.rx, 1e-3)
        assertEquals(1.0, e.ratio, 1e-3)
    }

    @Test
    fun leastSquaresPointFindsVanishingPoint() {
        val vp = V(1200.0, 300.0)
        val lines = listOf(V(100.0, 100.0), V(200.0, 600.0), V(50.0, 400.0)).map { p -> p to (vp - p) }
        val got = leastSquaresPoint(lines)!!
        assertEquals(vp.x, got.x, 1e-6)
        assertEquals(vp.y, got.y, 1e-6)
    }

    @Test
    fun parallelLinesHaveNoPoint() {
        val lines = listOf(V(0.0, 0.0) to V(1.0, 0.0), V(0.0, 10.0) to V(1.0, 0.0))
        assertNull(leastSquaresPoint(lines))
    }

    @Test
    fun lineAngleIsUndirected() {
        assertEquals(0.0, lineAngleDeg(V(1.0, 0.0), V(-1.0, 0.0)), 1e-9)
        assertEquals(10.0, lineAngleDeg(V(1.0, 0.0), V(cos(0.1745329), sin(0.1745329))), 1e-3)
        assertEquals(-10.0, lineAngleDeg(V(-1.0, 0.0), V(cos(-0.1745329), sin(-0.1745329))), 1e-3)
    }

    @Test
    fun clustersThreeBoxDirections() {
        val dirs = listOf(
            V(0.0, 1.0), V(0.02, 1.0), V(-0.01, 1.0),
            V(1.0, 0.3), V(1.0, 0.25), V(1.0, 0.35),
            V(1.0, -0.4), V(1.0, -0.45), V(1.0, -0.5),
        )
        val lab = clusterDirections(dirs, 3)
        assertEquals(lab[0], lab[1]); assertEquals(lab[0], lab[2])
        assertEquals(lab[3], lab[4]); assertEquals(lab[3], lab[5])
        assertEquals(lab[6], lab[7]); assertEquals(lab[6], lab[8])
        assertEquals(3, lab.toSet().size)
    }
}
