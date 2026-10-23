package dev.onilauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.onilauncher.controls.Action
import dev.onilauncher.controls.ActionKind
import dev.onilauncher.controls.ButtonControl
import dev.onilauncher.controls.Control
import dev.onilauncher.controls.ControlEditorDialog
import dev.onilauncher.controls.ControlOverlayView
import dev.onilauncher.controls.JoystickControl
import dev.onilauncher.controls.Layer
import dev.onilauncher.controls.LayerToggleControl
import dev.onilauncher.controls.Layout
import dev.onilauncher.controls.LayoutStore
import dev.onilauncher.controls.LoggingInputSink
import dev.onilauncher.controls.PressMode
import java.io.File

/**
 * Screen to try, edit and import touch layouts before the display backend exists.
 * Edit mode: drag a control to move it, tap it to edit its properties, use Add for new ones.
 * Events are only logged here.
 */
class ControlsActivity : Activity() {
    private lateinit var store: LayoutStore
    private lateinit var overlay: ControlOverlayView
    private lateinit var logView: TextView
    private lateinit var layoutBtn: Button
    private lateinit var editBtn: Button
    private var files: List<File> = emptyList()
    private var index = 0
    private val logLines = ArrayDeque<String>()
    private val importCode = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LayoutStore(this, LauncherPaths(this))
        store.ensureDefaults()
        files = store.list()

        overlay = ControlOverlayView(this, LoggingInputSink(::appendLog))
        overlay.onLayoutEdited = ::saveCurrent
        overlay.onControlTapped = { c -> editControl(c) }

        layoutBtn = Button(this).apply { setOnClickListener { cycleLayout() } }
        editBtn = Button(this).apply {
            setOnClickListener { setEditMode(!overlay.editMode) }
        }
        setEditMode(false)
        val addBtn = Button(this).apply { text = "Add"; setOnClickListener { showAddMenu() } }
        val layersBtn = Button(this).apply { text = "Layers"; setOnClickListener { showLayersDialog() } }
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

        val buttons = HorizontalScrollView(this).apply {
            addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(layoutBtn); addView(editBtn); addView(addBtn); addView(layersBtn); addView(importBtn)
            })
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(buttons)
            addView(logView)
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xFF202830.toInt())
            addView(overlay, ViewGroup.LayoutParams(-1, -1))
            addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.START))
        })
        loadCurrent()
    }

    private fun setEditMode(on: Boolean) {
        overlay.editMode = on
        editBtn.text = if (on) "Edit: on (drag = move, tap = edit)" else "Edit: off"
    }

    private fun loadCurrent() {
        val f = files.getOrNull(index) ?: return
        try {
            overlay.layout = store.load(f)
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

    private fun saveCurrent() {
        val layout = overlay.layout ?: return
        val file = files.getOrNull(index) ?: return
        try { store.save(file, layout) } catch (e: Exception) { appendLog("Save failed: ${e.message}") }
    }

    private fun editControl(c: Control) {
        val layout = overlay.layout ?: return
        ControlEditorDialog(this, layout).show(c) {
            saveCurrent()
            overlay.invalidate()
        }
    }

    // ---------- add / layers ----------

    private fun showAddMenu() {
        val layout = overlay.layout ?: return
        AlertDialog.Builder(this)
            .setTitle("Add")
            .setItems(arrayOf("Button", "Joystick", "Layer toggle", "New layer")) { _, which ->
                when (which) {
                    0 -> addControl(layout, ButtonControl(
                        newId(layout, "btn"), 0.4f, 0.4f, 56f, 48f, "Btn",
                        Action(ActionKind.KEY, "E"), PressMode.HOLD))
                    1 -> addControl(layout, JoystickControl(
                        newId(layout, "joy"), 0.1f, 0.4f, 140f, "W", "S", "A", "D"))
                    2 -> if (layout.layers.size < 2) appendLog("Create another layer first (Add > New layer)")
                         else addControl(layout, LayerToggleControl(
                            newId(layout, "layer"), 0.4f, 0.2f, 60f, 48f, "Layer", layout.layers.last().id))
                    3 -> promptNewLayer(layout)
                }
            }
            .show()
    }

    /** New controls go to the topmost visible layer (see Layers to choose which ones are visible). */
    private fun addControl(layout: Layout, control: Control) {
        setEditMode(true)
        if (layout.layers.isEmpty()) layout.layers.add(Layer("main", true, mutableListOf()))
        val target = layout.layers.lastOrNull { it.visible } ?: layout.layers.first()
        target.controls.add(control)
        saveCurrent()
        overlay.invalidate()
        editControl(control)
    }

    private fun newId(layout: Layout, prefix: String): String {
        val used = layout.layers.flatMap { it.controls }.map { it.id }.toSet()
        var n = 1
        while ("$prefix$n" in used) n++
        return "$prefix$n"
    }

    private fun promptNewLayer(layout: Layout) {
        val input = EditText(this).apply { hint = "layer name, e.g. combat"; setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle("New layer")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val id = input.text.toString().trim()
                when {
                    !Regex("[A-Za-z0-9_-]+").matches(id) -> appendLog("Layer name: letters, digits, _ and - only")
                    layout.layers.any { it.id == id } -> appendLog("Layer '$id' already exists")
                    else -> {
                        layout.layers.add(Layer(id, false, mutableListOf()))
                        saveCurrent()
                        appendLog("Layer '$id' created (hidden). Add a Layer toggle for it, and show it in Layers to add controls.")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Visibility chosen here is saved as the layout's starting state. */
    private fun showLayersDialog() {
        val layout = overlay.layout ?: return
        val names = layout.layers.map { it.id }.toTypedArray()
        val checked = BooleanArray(names.size) { layout.layers[it].visible }
        AlertDialog.Builder(this)
            .setTitle("Visible layers")
            .setMultiChoiceItems(names, checked) { _, i, isChecked ->
                layout.layers[i].visible = isChecked
                overlay.invalidate()
                saveCurrent()
            }
            .setPositiveButton("OK", null)
            .show()
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
