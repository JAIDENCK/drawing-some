package com.gridline.app.ui

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("gridline", Context.MODE_PRIVATE)

    /** Only the pen draws; fingers pan and zoom. Default on: it's the palm rejection. */
    var penOnly: Boolean
        get() = sp.getBoolean("penOnly", true)
        set(v) = sp.edit().putBoolean("penOnly", v).apply()

    var hover: Boolean
        get() = sp.getBoolean("hover", true)
        set(v) = sp.edit().putBoolean("hover", v).apply()

    var predict: Boolean
        get() = sp.getBoolean("predict", true)
        set(v) = sp.edit().putBoolean("predict", v).apply()

    var swapButtons: Boolean
        get() = sp.getBoolean("swapButtons", false)
        set(v) = sp.edit().putBoolean("swapButtons", v).apply()

    /** 0 short, 1 normal, 2 long. */
    var sessionLength: Int
        get() = sp.getInt("len", 1)
        set(v) = sp.edit().putInt("len", v).apply()
}
