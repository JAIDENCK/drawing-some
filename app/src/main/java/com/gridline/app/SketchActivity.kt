package com.gridline.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.gridline.app.sketch.SketchView
import com.gridline.app.ui.Colors
import com.gridline.app.ui.History
import com.gridline.app.ui.Prefs
import com.gridline.app.ui.SessionRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class SketchActivity : Activity(), SketchView.Listener {
    private lateinit var sketch: SketchView
    private lateinit var penBtn: Button
    private lateinit var eraserBtn: Button
    private lateinit var undoBtn: Button
    private lateinit var redoBtn: Button
    private lateinit var guideBtn: Button
    private lateinit var sizeBar: SeekBar
    private lateinit var sizeLabel: TextView
    private lateinit var swatches: LinearLayout

    private val palette = intArrayOf(
        Colors.INK,
        0xFF6E6E6E.toInt(),
        0xFFB5B5B5.toInt(), // light grey for construction lines
        0xFF2E5A8C.toInt(),
        0xFFA63A2E.toInt(),
    )

    private var strokeCount = 0
    private var activeMs = 0L
    private var lastStrokeAt = 0L

    private val autosave by lazy { File(filesDir, "sketch.png") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sketch)
        sketch = findViewById(R.id.sketchView)
        penBtn = findViewById(R.id.pen)
        eraserBtn = findViewById(R.id.eraser)
        undoBtn = findViewById(R.id.undo)
        redoBtn = findViewById(R.id.redo)
        guideBtn = findViewById(R.id.guide)
        sizeBar = findViewById(R.id.size)
        sizeLabel = findViewById(R.id.sizeLabel)
        swatches = findViewById(R.id.swatches)

        val prefs = Prefs(this)
        sketch.penOnly = prefs.penOnly
        sketch.showHover = prefs.hover
        sketch.predict = prefs.predict
        sketch.swapButtons = prefs.swapButtons
        sketch.listener = this

        if (autosave.exists()) {
            BitmapFactory.decodeFile(autosave.path)?.let { sketch.loadBase(it) }
        }

        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        penBtn.setOnClickListener { sketch.tool = SketchView.Tool.PEN; refresh() }
        eraserBtn.setOnClickListener { sketch.tool = SketchView.Tool.ERASER; refresh() }
        undoBtn.setOnClickListener { sketch.undo() }
        redoBtn.setOnClickListener { sketch.redo() }
        guideBtn.setOnClickListener { sketch.guide.next(); sketch.invalidate(); refresh() }
        findViewById<Button>(R.id.fit).setOnClickListener { sketch.fit() }
        findViewById<Button>(R.id.clear).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Clear the page? Undo can bring it back.")
                .setPositiveButton("Clear") { _, _ -> sketch.clearPage() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<Button>(R.id.save).setOnClickListener { saveToGallery() }

        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = (progress + 1).toFloat()
                if (sketch.tool == SketchView.Tool.ERASER) sketch.eraserSize = v * 2 else sketch.penSize = v
                refresh()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        buildSwatches()
        refresh()
    }

    private fun buildSwatches() {
        val d = resources.displayMetrics.density
        swatches.removeAllViews()
        for (c in palette) {
            val v = View(this)
            val size = (26 * d).toInt()
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins((5 * d).toInt(), 0, (5 * d).toInt(), 0) }
            v.setOnClickListener {
                sketch.color = c
                sketch.tool = SketchView.Tool.PEN
                refresh()
            }
            v.tag = c
            swatches.addView(v)
        }
    }

    private fun refresh() {
        val d = resources.displayMetrics.density
        val accent = getColor(R.color.accent)
        val text = getColor(R.color.text)
        val erasing = sketch.tool == SketchView.Tool.ERASER
        penBtn.setTextColor(if (!erasing) accent else text)
        eraserBtn.setTextColor(if (erasing) accent else text)
        val size = if (erasing) sketch.eraserSize / 2 else sketch.penSize
        sizeBar.progress = (size.toInt() - 1).coerceIn(0, sizeBar.max)
        sizeLabel.text = "Size ${if (erasing) sketch.eraserSize.toInt() else sketch.penSize.toInt()}"
        undoBtn.isEnabled = sketch.canUndo
        redoBtn.isEnabled = sketch.canRedo
        undoBtn.alpha = if (sketch.canUndo) 1f else 0.4f
        redoBtn.alpha = if (sketch.canRedo) 1f else 0.4f
        guideBtn.text = "Guide: ${sketch.guide.mode.label}"
        for (i in 0 until swatches.childCount) {
            val v = swatches.getChildAt(i)
            val c = v.tag as Int
            val selected = !erasing && c == sketch.color
            v.background = GradientDrawable().apply {
                setColor(c)
                cornerRadius = 4 * d
                setStroke(((if (selected) 3 else 1) * d).toInt(), if (selected) accent else 0xFFB0ABA2.toInt())
            }
        }
    }

    override fun onStrokeCommitted() {
        val now = SystemClock.elapsedRealtime()
        if (lastStrokeAt != 0L && now - lastStrokeAt < 120_000) activeMs += now - lastStrokeAt
        lastStrokeAt = now
        strokeCount++
    }

    override fun onUndoStateChanged() = refresh()

    private fun saveToGallery() {
        val bmp = sketch.exportBitmap() ?: return
        thread {
            val name = "gridline_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Gridline")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val ok = try {
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("no uri")
                contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                true
            } catch (_: Exception) {
                false
            }
            bmp.recycle()
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) "Saved to Pictures/Gridline" else "Couldn't save the image.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val snap = sketch.snapshot()
        if (snap != null) {
            val file = autosave
            thread {
                try {
                    val tmp = File(file.path + ".tmp")
                    tmp.outputStream().use { snap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    tmp.renameTo(file)
                } catch (_: Exception) {
                }
                snap.recycle()
            }
        }
        if (isFinishing && strokeCount >= 3) {
            History(this).add(
                SessionRecord(System.currentTimeMillis(), "sketch", strokeCount, (activeMs / 1000).toInt(), emptyMap()),
            )
            strokeCount = 0
        }
    }
}
