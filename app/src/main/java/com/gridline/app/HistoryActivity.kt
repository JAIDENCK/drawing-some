package com.gridline.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import com.gridline.app.drill.Drills
import com.gridline.app.ui.History
import com.gridline.app.ui.SparkView
import com.gridline.app.ui.Ui
import com.gridline.app.ui.f
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { fitsSystemWindows = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(this@HistoryActivity, 12f), 0, Ui.dp(this@HistoryActivity, 36f))
        }
        scroll.addView(col)
        setContentView(scroll)
        Ui.centerColumn(this, col)

        col.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        col.addView(Ui.text(this, "History", 28f, getColor(R.color.text)))

        val recs = History(this).load()
        if (recs.isEmpty()) {
            col.addView(Ui.text(this, "Nothing logged yet. Finish a drill and it shows up here.", 15f, getColor(R.color.muted)))
            return
        }

        Ui.header(this, col, "Trends (last 20 sessions, lower is better)")
        var anyTrend = false
        for (t in Drills.types) {
            val d = Drills.headline(t) ?: continue
            val vals = recs.filter { it.type == t }.mapNotNull { it.values[d.key] }.takeLast(20)
            if (vals.size < 2) continue
            anyTrend = true
            col.addView(Ui.text(this, "${Drills.title(t)} — ${d.label.lowercase()} (${d.unit})", 15f, getColor(R.color.text)))
            col.addView(SparkView(this).apply { set(vals, if (d.unit == "°") "°" else "", d.decimals) })
        }
        if (!anyTrend) {
            col.addView(Ui.text(this, "Trends appear after two sessions of the same drill.", 14f, getColor(R.color.muted)))
        }

        val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
        val byDay = recs.groupBy { it.day }.toSortedMap(compareByDescending { it })
        for ((day, list) in byDay) {
            val mins = (list.sumOf { it.secs } + 59) / 60
            Ui.header(this, col, "${day.format(fmt)} · ${list.size} session${if (list.size == 1) "" else "s"} · $mins min")
            for (r in list.reversed()) {
                val defs = Drills.metrics(r.type)
                val detail = if (defs.isEmpty()) {
                    "${r.reps} strokes, ${(r.secs + 59) / 60} min"
                } else {
                    defs.mapNotNull { d ->
                        r.values[d.key]?.let { "${d.label.lowercase()} ${f(it, d.decimals)}${if (d.unit.isEmpty()) "" else " " + d.unit}" }
                    }.joinToString(" · ")
                }
                Ui.row(this, col, "${Drills.title(r.type)} · ${r.reps} ${if (r.type == "sketch") "strokes" else "reps"}", detail, null)
            }
        }
    }
}
