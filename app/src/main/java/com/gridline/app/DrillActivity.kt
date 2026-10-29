package com.gridline.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.gridline.app.drill.Attempt
import com.gridline.app.drill.Drill
import com.gridline.app.drill.DrillEnv
import com.gridline.app.drill.DrillView
import com.gridline.app.drill.Drills
import com.gridline.app.ink.Stroke
import com.gridline.app.ui.History
import com.gridline.app.ui.Prefs
import com.gridline.app.ui.SessionRecord
import com.gridline.app.ui.f

class DrillActivity : Activity(), DrillView.Listener {
    private lateinit var view: DrillView
    private lateinit var drill: Drill
    private lateinit var counter: TextView
    private lateinit var feedback: TextView
    private lateinit var guideBtn: Button
    private lateinit var undoBtn: Button
    private lateinit var checkBtn: Button

    private val attempts = ArrayList<Attempt>()
    private var target = 20
    private var chain: List<Pair<String, Int>> = emptyList()
    private var firstStrokeAt = 0L
    private var lastActivityAt = 0L
    private var summaryShown = false
    private var saved = false
    private var swapButtons = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drill)

        val plan = parsePlan(intent.getStringExtra(EXTRA_PLAN) ?: "lines:20")
        val (type, reps) = plan.first()
        chain = plan.drop(1)
        target = reps

        val dm = resources.displayMetrics
        var dpi = (dm.xdpi + dm.ydpi) / 2
        if (dpi < 100 || dpi > 800) dpi = dm.densityDpi.toFloat()
        drill = Drills.create(type, DrillEnv(dpi / 25.4f, dm.density))

        view = findViewById(R.id.drillView)
        counter = findViewById(R.id.counter)
        feedback = findViewById(R.id.feedback)
        guideBtn = findViewById(R.id.guide)
        undoBtn = findViewById(R.id.undo)
        checkBtn = findViewById(R.id.check)
        findViewById<TextView>(R.id.title).text = drill.title

        val prefs = Prefs(this)
        view.penOnly = prefs.penOnly
        view.showHover = prefs.hover
        view.predict = prefs.predict
        swapButtons = prefs.swapButtons
        view.listener = this
        view.bind(drill)

        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        findViewById<Button>(R.id.done).setOnClickListener {
            if (attempts.isEmpty()) finish() else showSummary()
        }
        guideBtn.setOnClickListener { drill.cycleGuide(); refresh() }
        undoBtn.setOnClickListener { undo() }
        checkBtn.setOnClickListener { checkOrNext() }
        refresh()
    }

    private fun refresh() {
        counter.text = "${attempts.size} / $target"
        val g = drill.guideLabel
        guideBtn.visibility = if (g == null) View.GONE else View.VISIBLE
        guideBtn.text = "Guide: $g"
        undoBtn.text = if (drill.multiStroke) "Undo" else "Discard last"
        checkBtn.visibility = if (drill.multiStroke) View.VISIBLE else View.GONE
        checkBtn.text = if (drill.checked) "Next box" else "Check"
        feedback.text = drill.feedback
        view.invalidate()
    }

    private fun undo() {
        if (drill.multiStroke) {
            drill.undoStroke()
        } else if (attempts.isNotEmpty()) {
            attempts.removeAt(attempts.size - 1)
            drill.discardLast()
        }
        refresh()
    }

    private fun checkOrNext() {
        if (!drill.multiStroke) return
        if (drill.checked) {
            if (attempts.size >= target && !summaryShown) {
                showSummary()
                return
            }
            drill.newRound()
        } else {
            drill.score()?.let { attempts.add(it); lastActivityAt = SystemClock.elapsedRealtime() }
        }
        refresh()
    }

    // ---- DrillView.Listener ---------------------------------------------

    override fun onStrokeStart() {
        if (firstStrokeAt == 0L) firstStrokeAt = SystemClock.elapsedRealtime()
    }

    override fun onStroke(s: Stroke) {
        lastActivityAt = SystemClock.elapsedRealtime()
        if (drill.multiStroke && drill.checked) {
            Toast.makeText(this, "Already checked. Tap Next box.", Toast.LENGTH_SHORT).show()
            return
        }
        if (drill.addStroke(s)) {
            val a = drill.score()
            if (a != null) {
                attempts.add(a)
                drill.newRound()
                if (attempts.size >= target && !summaryShown) {
                    refresh()
                    showSummary()
                    return
                }
            }
        }
        refresh()
    }

    override fun onPenButton(button: Int) {
        val undoButton = if (swapButtons) MotionEvent.BUTTON_STYLUS_SECONDARY else MotionEvent.BUTTON_STYLUS_PRIMARY
        val checkButton = if (swapButtons) MotionEvent.BUTTON_STYLUS_PRIMARY else MotionEvent.BUTTON_STYLUS_SECONDARY
        when (button) {
            undoButton -> undo()
            checkButton -> checkOrNext()
        }
    }

    override fun onRoundReset() = refresh()

    // ---- session ----------------------------------------------------------

    private fun averages(): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (d in drill.metrics) {
            val vs = attempts.mapNotNull { it.values[d.key] }.filter { !it.isNaN() }
            out[d.key] = if (vs.isEmpty()) Double.NaN else vs.average()
        }
        return out
    }

    private fun secs(): Int =
        if (firstStrokeAt == 0L) 0 else ((lastActivityAt - firstStrokeAt) / 1000).toInt().coerceAtLeast(0)

    private fun summaryText(): String {
        val avg = averages()
        val prev = History(this).load().lastOrNull { it.type == drill.type }
        val sb = StringBuilder()
        val mins = secs() / 60
        sb.append("${attempts.size} reps").append(if (mins > 0) ", $mins min" else "").append("\n\n")
        for (d in drill.metrics) {
            val v = avg[d.key] ?: continue
            if (v.isNaN()) continue
            val unit = if (d.unit.isEmpty()) "" else " ${d.unit}"
            sb.append("${d.label}: ${f(v, d.decimals)}$unit")
            prev?.values?.get(d.key)?.let { p -> sb.append("   (last session ${f(p, d.decimals)})") }
            sb.append('\n')
        }
        sb.append("\nAverages per rep. Lower is better for all of them.")
        return sb.toString()
    }

    private fun showSummary() {
        summaryShown = true
        val b = AlertDialog.Builder(this)
            .setTitle(drill.title)
            .setMessage(summaryText())
            .setNegativeButton("Keep going") { _, _ ->
                if (drill.multiStroke && drill.checked) drill.newRound()
                refresh()
            }
        if (chain.isNotEmpty()) {
            b.setPositiveButton("Next: ${Drills.title(chain[0].first)}") { _, _ ->
                save()
                startActivity(intent(this, chain.joinToString(",") { "${it.first}:${it.second}" }))
                finish()
            }
        } else {
            b.setPositiveButton("Save & close") { _, _ -> finish() }
        }
        b.show()
    }

    private fun save() {
        if (saved || attempts.isEmpty()) return
        saved = true
        History(this).add(SessionRecord(System.currentTimeMillis(), drill.type, attempts.size, secs(), averages()))
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) save()
    }

    companion object {
        private const val EXTRA_PLAN = "plan"

        /** Plan format: "type:reps,type:reps", run in order. */
        fun intent(ctx: Context, plan: String): Intent =
            Intent(ctx, DrillActivity::class.java).putExtra(EXTRA_PLAN, plan)

        private fun parsePlan(s: String): List<Pair<String, Int>> {
            val out = s.split(",").mapNotNull { part ->
                val bits = part.trim().split(":")
                if (bits[0].isEmpty()) null else bits[0] to (bits.getOrNull(1)?.toIntOrNull() ?: 10)
            }
            return out.ifEmpty { listOf("lines" to 20) }
        }
    }
}
