package com.gridline.app.ink

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.MotionPredictor

/**
 * Wraps Android 14's MotionPredictor. The predicted tail is only drawn on screen
 * to hide latency; it is never stored or scored.
 */
class InkPredictor(context: Context) {
    private val impl: Any? = if (Build.VERSION.SDK_INT >= 34) Api34.create(context) else null
    var enabled = true

    fun record(e: MotionEvent) {
        if (enabled && impl != null && Build.VERSION.SDK_INT >= 34) {
            try { Api34.record(impl, e) } catch (_: RuntimeException) { }
        }
    }

    fun predict(): MotionEvent? {
        if (!enabled || impl == null || Build.VERSION.SDK_INT < 34) return null
        return try { Api34.predict(impl) } catch (_: RuntimeException) { null }
    }

    @TargetApi(34)
    private object Api34 {
        fun create(c: Context): Any = MotionPredictor(c)
        fun record(p: Any, e: MotionEvent) = (p as MotionPredictor).record(e)
        fun predict(p: Any): MotionEvent? = (p as MotionPredictor).predict(System.nanoTime())
    }
}
