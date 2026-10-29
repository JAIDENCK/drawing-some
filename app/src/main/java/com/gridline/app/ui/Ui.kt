package com.gridline.app.ui

import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gridline.app.R

/** Tiny helpers for the plain list-style screens. */
object Ui {
    fun dp(a: Activity, v: Float) = (v * a.resources.displayMetrics.density).toInt()

    /** Keeps text columns readable on a big tablet by centering a ~720dp column. */
    fun centerColumn(a: Activity, v: View) {
        val w = a.resources.displayMetrics.widthPixels
        val side = maxOf(dp(a, 24f), (w - dp(a, 720f)) / 2)
        v.setPadding(side, v.paddingTop, side, v.paddingBottom)
    }

    fun row(a: Activity, parent: LinearLayout, title: String, sub: String?, onClick: (() -> Unit)?) {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(a, 14f), 0, dp(a, 14f))
            if (onClick != null) {
                isClickable = true
                val tv = TypedValue()
                a.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                setOnClickListener { onClick() }
            }
        }
        box.addView(text(a, title, 18f, a.getColor(R.color.text)))
        if (!sub.isNullOrEmpty()) {
            box.addView(text(a, sub, 14f, a.getColor(R.color.muted)).apply { setPadding(0, dp(a, 3f), 0, 0) })
        }
        parent.addView(box)
        divider(a, parent)
    }

    fun text(a: Activity, s: String, sp: Float, color: Int) = TextView(a).apply {
        text = s
        textSize = sp
        setTextColor(color)
    }

    fun header(a: Activity, parent: LinearLayout, s: String) {
        parent.addView(text(a, s, 14f, a.getColor(R.color.muted)).apply {
            setPadding(0, dp(a, 22f), 0, dp(a, 6f))
            isAllCaps = true
            letterSpacing = 0.06f
        })
    }

    fun divider(a: Activity, parent: ViewGroup) {
        parent.addView(View(a).apply {
            setBackgroundColor(a.getColor(R.color.divider))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 1f))
        })
    }

}
