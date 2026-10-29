package com.gridline.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.gridline.app.drill.Drills
import com.gridline.app.ui.History
import com.gridline.app.ui.Prefs
import com.gridline.app.ui.SessionRecord
import com.gridline.app.ui.Ui
import com.gridline.app.ui.f
import java.time.LocalDate

class MainActivity : Activity() {
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rows = findViewById(R.id.rows)
        status = findViewById(R.id.status)
        Ui.centerColumn(this, findViewById(R.id.content))
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Ui.centerColumn(this, findViewById(R.id.content))
    }

    private fun render() {
        val prefs = Prefs(this)
        val recs = History(this).load()
        status.text = statusLine(recs)
        rows.removeAllViews()
        val len = prefs.sessionLength

        val plan = Drills.warmupPlan(len)
        val parts = plan.split(",").map { it.split(":") }
        val warmDesc = parts.joinToString(" → ") { (t, n) ->
            "$n ${Drills.title(t).lowercase().replace("boxes · 2-point", "boxes")}"
        }
        Ui.row(this, rows, "Daily warm-up", warmDesc) { startActivity(DrillActivity.intent(this, plan)) }
        for (t in Drills.types) {
            Ui.row(this, rows, Drills.title(t), lastLine(recs, t) ?: Drills.blurb(t)) {
                startActivity(DrillActivity.intent(this, "$t:${Drills.reps(t, len)}"))
            }
        }
        Ui.row(this, rows, "Sketch", "Pressure pen, eraser, undo, pinch zoom, perspective guides.") {
            startActivity(Intent(this, SketchActivity::class.java))
        }
        Ui.row(this, rows, "History", if (recs.isEmpty()) "Nothing logged yet." else "${recs.size} sessions logged.") {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        Ui.row(this, rows, "Session length: ${Drills.lengthNames[len]}", "Reps per drill.") { pickLength() }
        Ui.row(this, rows, "Settings", settingsSummary(prefs)) { showSettings() }
        Ui.row(this, rows, "Pen & tips", "Side buttons, and what the numbers mean.") { showTips() }
    }

    private fun statusLine(recs: List<SessionRecord>): String {
        if (recs.isEmpty()) return "Nothing logged yet. Ten minutes a day goes a long way."
        val today = LocalDate.now()
        val todays = recs.filter { it.day == today }
        val streak = History.streak(recs)
        val streakText = if (streak > 1) " · $streak days in a row" else ""
        return if (todays.isEmpty()) {
            "Not practised today yet · last session ${History.ago(recs.last().day)}$streakText"
        } else {
            val mins = (todays.sumOf { it.secs } + 59) / 60
            "Today: ${todays.size} session${if (todays.size == 1) "" else "s"}, $mins min$streakText"
        }
    }

    private fun lastLine(recs: List<SessionRecord>, type: String): String? {
        val r = recs.lastOrNull { it.type == type } ?: return null
        val defs = Drills.metrics(type).take(2)
        val vals = defs.mapNotNull { d ->
            r.values[d.key]?.let { "${d.label.lowercase()} ${f(it, d.decimals)}${if (d.unit.isEmpty()) "" else " " + d.unit}" }
        }
        return "Last: ${vals.joinToString(" · ")} · ${History.ago(r.day)}"
    }

    private fun settingsSummary(p: Prefs): String {
        val on = ArrayList<String>()
        on.add(if (p.penOnly) "Pen only" else "Fingers draw too")
        if (p.hover) on.add("hover cursor")
        if (p.predict) on.add("ink prediction")
        if (p.swapButtons) on.add("buttons swapped")
        return on.joinToString(" · ")
    }

    private fun pickLength() {
        val p = Prefs(this)
        AlertDialog.Builder(this)
            .setTitle("Session length")
            .setSingleChoiceItems(Drills.lengthNames, p.sessionLength) { d, which ->
                p.sessionLength = which
                d.dismiss()
                render()
            }
            .show()
    }

    private fun showSettings() {
        val p = Prefs(this)
        val items = arrayOf(
            "Pen only (fingers pan and zoom, never draw)",
            "Hover cursor",
            "Ink prediction (hides some lag, Android 14+)",
            "Swap pen side buttons",
        )
        val checked = booleanArrayOf(p.penOnly, p.hover, p.predict, p.swapButtons)
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setMultiChoiceItems(items, checked) { _, which, on ->
                when (which) {
                    0 -> p.penOnly = on
                    1 -> p.hover = on
                    2 -> p.predict = on
                    3 -> p.swapButtons = on
                }
            }
            .setPositiveButton("Done") { _, _ -> render() }
            .setOnDismissListener { render() }
            .show()
    }

    private fun showTips() {
        val msg = """
            PEN BUTTONS
            Sketch: hold the first side button while drawing to erase. Press the second to undo.
            Drills: first button = undo / discard last. Second button = Check / Next box.
            "First" is usually the one nearer the tip. Use "Swap pen side buttons" in Settings if it's backwards. If a button opens a Wacom or system menu instead, set it back to its default action in the tablet's pen settings.

            WHAT THE NUMBERS MEAN
            Wobble: shake around a smooth path. Comes from drawing slowly or from the fingers.
            Bow: how much a line curves. Usually the wrist or elbow pivoting.
            Box angle error: how far each edge points away from where it should. After Check, every edge is extended across the page so you can see where it actually goes; the dashed green line is where it should have gone.
            "Converge too fast": the line would hit the horizon before the vanishing point. "Too parallel": past it.

            HOW TO PRACTISE
            Ghost the motion two or three times above the screen, then draw it in one go.
            Long lines come from the shoulder. Keep the wrist quiet.
            Don't fix a bad line by stroking over it. Draw a new one.
            A few minutes every day beats an hour on Sunday.
        """.trimIndent()
        AlertDialog.Builder(this).setTitle("Pen & tips").setMessage(msg).setPositiveButton("OK", null).show()
    }
}
