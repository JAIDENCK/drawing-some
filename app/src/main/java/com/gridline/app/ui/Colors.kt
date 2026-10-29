package com.gridline.app.ui

import java.util.Locale

object Colors {
    const val PAPER = 0xFFF7F5F0.toInt()
    const val DESK = 0xFFD9D5CD.toInt()
    const val INK = 0xFF1E1E1E.toInt()
    const val INK_FADED = 0x881E1E1E.toInt()
    const val MUTED = 0xFF77736C.toInt()
    const val GUIDE = 0xFF8FA3B5.toInt()
    const val GUIDE_LIGHT = 0x668FA3B5
    const val GOOD = 0xFF2F7D5B.toInt()
    const val WARN = 0xFFB7811C.toInt()
    const val BAD = 0xFFB4473A.toInt()
    const val SET_BLUE = 0xFF3E6FA8.toInt()
    const val SET_RUST = 0xFFA8603E.toInt()
    const val SET_GREY = 0xFF6F6F6F.toInt()
    const val SET_PURPLE = 0xFF7A5FA0.toInt()

    fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    fun severity(errDeg: Double) = when {
        errDeg <= 1.5 -> GOOD
        errDeg <= 4.0 -> WARN
        else -> BAD
    }
}

fun f(v: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", v)
