package dev.onilauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.onilauncher.controls.ControlOverlayView
import dev.onilauncher.controls.LayoutStore
import dev.onilauncher.controls.LoggingInputSink
import java.io.File

/**
 * Standalone screen to try, edit and import touch layouts before the display
 * backend exists. Events are only logged here. Later the same overlay sits on
 * top of the game surface with a real InputSink.
 */
class ControlsActivity : Activity() {
    private lateinit var store: LayoutStore
    private lateinit var ctrlOverlay: ControlOverlayView
    private lateinit var logView: TextView
    private lateinit var layoutBtn: Button
    private lateinit var editBtn: Button
    private var files: List<File> = emptyList()
    private var index = 0
    private val logLines = ArrayDeque<String>()
    private val importCode = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LayoutStore(this)
        store.ensureDefaults()
        files = store.list()

        ctrlOverlay = ControlOverlayView(this, LoggingInputSink(::appendLog))
        ctrlOverlay.onLayoutEdited = {
            ctrlOverlay.layout?.let { l -> files.getOrNull(index)?.let { f -> store.save(f, l) } }
        }

        layoutBtn = Button(this).apply { setOnClickListener { cycleLayout() } }
        editBtn = Button(this).apply {
            text = "Edit: off"
            setOnClickListener {
                ctrlOverlay.editMode = !ctrlOverlay.editMode
                text = if (ctrlOverlay.editMode) "Edit: on (drag controls)" else "Edit: off"
            }
        }
        val importBtn = Button(this).apply {
            text = "Import JSON"
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                    importCode,
                )
            }
        }
        logView = TextView(this).apply { textSize = 11f; setTextColor(0xFFFFFFFF.toInt()) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(layoutBtn); addView(editBtn); addView(importBtn)
            })
            addView(logView)
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xFF202830.toInt())
            addView(ctrlOverlay, ViewGroup.LayoutParams(-1, -1))
            addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        })
        loadCurrent()
    }

    private fun loadCurrent() {
        val f = files.getOrNull(index) ?: return
        try {
            ctrlOverlay.layout = store.load(f)
            layoutBtn.text = "Layout: ${f.nameWithoutExtension}"
        } catch (e: Exception) {
            appendLog("Layout error (${f.name}): ${e.message}")
        }
    }

    private fun cycleLayout() {
        if (files.isEmpty()) return
        index = (index + 1) % files.size
        loadCurrent()
    }

    private fun appendLog(line: String) = runOnUiThread {
        logLines.addLast(line)
        while (logLines.size > 6) logLines.removeFirst()
        logView.text = logLines.joinToString("\n")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != importCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val file = store.importFrom(uri)
            files = store.list()
            index = files.indexOfFirst { it.name == file.name }.coerceAtLeast(0)
            loadCurrent()
            appendLog("Imported ${file.name}")
        } catch (e: Exception) {
            appendLog("Import failed: ${e.message}")
        }
    }
}
