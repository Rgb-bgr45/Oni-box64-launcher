package dev.onilauncher.controls

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** Property editor for one control: label, action, size, delete. Values are validated before applying. */
class ControlEditorDialog(private val activity: Activity, private val layout: Layout) {

    fun show(c: Control, onChanged: () -> Unit) {
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        fun label(text: String) = form.addView(TextView(activity).apply { this.text = text })

        fun field(title: String, value: String, numeric: Boolean = false): EditText {
            label(title)
            val e = EditText(activity).apply {
                setText(value)
                setSingleLine()
                if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            form.addView(e)
            return e
        }

        fun spinner(title: String, items: List<String>, selected: Int): Spinner {
            label(title)
            val sp = Spinner(activity).apply {
                adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, items)
                setSelection(selected.coerceAtLeast(0))
            }
            form.addView(sp)
            return sp
        }

        var apply: () -> Unit = {}

        when (c) {
            is ButtonControl -> {
                val name = field("Label", c.label)
                val kind = spinner("Action type", listOf("key", "mouse", "scroll"), c.action.kind.ordinal)
                val value = field("Value (key name, left/right/middle, or up/down)", c.action.value)
                val toggle = CheckBox(activity).apply {
                    text = "Toggle mode (tap on / tap off)"
                    isChecked = c.mode == PressMode.TOGGLE
                }
                form.addView(toggle)
                val w = field("Width (dp)", fmt(c.widthDp), true)
                val h = field("Height (dp)", fmt(c.heightDp), true)
                apply = {
                    val action = LayoutJson.makeAction(c.id, kind.selectedItem as String, value.text.toString())
                    val newW = dim(w, "Width", 24f, 300f)
                    val newH = dim(h, "Height", 24f, 300f)
                    c.label = name.text.toString().ifBlank { c.id }
                    c.action = action
                    c.mode = if (toggle.isChecked) PressMode.TOGGLE else PressMode.HOLD
                    c.widthDp = newW
                    c.heightDp = newH
                }
            }
            is JoystickControl -> {
                val size = field("Size (dp)", fmt(c.sizeDp), true)
                val up = field("Up key", c.up)
                val down = field("Down key", c.down)
                val left = field("Left key", c.left)
                val right = field("Right key", c.right)
                apply = {
                    val newSize = dim(size, "Size", 60f, 400f)
                    val u = LayoutJson.keyName(c.id, up.text.toString())
                    val d = LayoutJson.keyName(c.id, down.text.toString())
                    val l = LayoutJson.keyName(c.id, left.text.toString())
                    val r = LayoutJson.keyName(c.id, right.text.toString())
                    c.sizeDp = newSize
                    c.up = u; c.down = d; c.left = l; c.right = r
                }
            }
            is LayerToggleControl -> {
                val name = field("Label", c.label)
                val ids = layout.layers.map { it.id }
                val target = spinner("Layer to show/hide", ids, ids.indexOf(c.target))
                val w = field("Width (dp)", fmt(c.widthDp), true)
                val h = field("Height (dp)", fmt(c.heightDp), true)
                apply = {
                    val newW = dim(w, "Width", 24f, 300f)
                    val newH = dim(h, "Height", 24f, 300f)
                    val picked = target.selectedItem as? String
                        ?: throw IllegalArgumentException("No layer selected")
                    c.label = name.text.toString().ifBlank { c.id }
                    c.target = picked
                    c.widthDp = newW
                    c.heightDp = newH
                }
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Edit ${c.id}")
            .setView(ScrollView(activity).apply { addView(form) })
            .setPositiveButton("Save", null)
            .setNeutralButton("Delete", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()

        // Set after show() so invalid input keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
            try {
                apply()
                onChanged()
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(activity, e.message ?: "Invalid value", Toast.LENGTH_LONG).show()
            }
        })
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(View.OnClickListener {
            layout.layers.forEach { it.controls.remove(c) }
            onChanged()
            dialog.dismiss()
        })
    }

    private fun dim(e: EditText, name: String, min: Float, max: Float): Float {
        val v = e.text.toString().toFloatOrNull()
            ?: throw IllegalArgumentException("$name must be a number")
        require(v in min..max) { "$name must be between ${fmt(min)} and ${fmt(max)}" }
        return v
    }

    private fun fmt(f: Float): String = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()
}
