package com.gridline.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SessionRecord(
    val time: Long,
    val type: String,
    val reps: Int,
    val secs: Int,
    val values: Map<String, Double>,
) {
    val day: LocalDate get() = dayOf(time)

    fun toJson(): JSONObject = JSONObject().apply {
        put("t", time)
        put("type", type)
        put("reps", reps)
        put("secs", secs)
        val m = JSONObject()
        for ((k, v) in values) if (!v.isNaN() && !v.isInfinite()) m.put(k, v)
        put("m", m)
    }

    companion object {
        fun fromJson(o: JSONObject): SessionRecord {
            val map = HashMap<String, Double>()
            o.optJSONObject("m")?.let { m -> m.keys().forEach { k -> map[k] = m.getDouble(k) } }
            return SessionRecord(o.getLong("t"), o.getString("type"), o.optInt("reps"), o.optInt("secs"), map)
        }

        fun dayOf(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}

/** Practice log as one small JSON file in app storage. Oldest first. */
class History(context: Context) {
    private val file = File(context.filesDir, "history.json")

    fun load(): List<SessionRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                try { SessionRecord.fromJson(arr.getJSONObject(i)) } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(r: SessionRecord) {
        val all = load().toMutableList()
        all.add(r)
        while (all.size > 3000) all.removeAt(0)
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    companion object {
        /** Consecutive days with any practice, counting back from today (or yesterday). */
        fun streak(recs: List<SessionRecord>): Int {
            val days = recs.map { it.day }.toHashSet()
            var d = LocalDate.now()
            if (d !in days) d = d.minusDays(1)
            var n = 0
            while (d in days) { n++; d = d.minusDays(1) }
            return n
        }

        fun ago(day: LocalDate): String {
            val diff = LocalDate.now().toEpochDay() - day.toEpochDay()
            return when (diff) {
                0L -> "today"
                1L -> "yesterday"
                else -> "$diff days ago"
            }
        }
    }
}
