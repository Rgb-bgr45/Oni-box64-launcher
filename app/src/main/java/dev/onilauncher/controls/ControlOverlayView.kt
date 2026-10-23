package dev.onilauncher.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Draws the layout and turns touches into InputSink events.
 * Touches that hit no control act as a touchpad: drag = move cursor,
 * tap = left click, two-finger tap = right click, press-and-hold then drag = left drag
 * (needed for drag-selecting tool areas).
 */
class ControlOverlayView(ctx: Context, private val sink: InputSink) : View(ctx) {

    var layout: Layout? = null
        set(value) { releaseAll(); field = value; invalidate() }
    var editMode: Boolean = false
        set(value) { releaseAll(); field = value; invalidate() }
    var onLayoutEdited: (() -> Unit)? = null
    /** Edit mode: a control was tapped (not dragged). */
    var onControlTapped: ((Control) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val cursorSensitivity = 1.5f
    private val tapMaxMs = 250L
    private val holdMs = 350L
    private val slop = 8f * density

    private val owners = SparseArray<Control>()
    private val pressed = HashSet<Control>()
    private val toggled = HashSet<Control>()
    private val joyKeys = HashMap<JoystickControl, Set<String>>()
    private val joyThumb = HashMap<JoystickControl, PointF>()

    private class CursorPtr(val startX: Float, val startY: Float, val startTime: Long) {
        var lastX = startX
        var lastY = startY
        var moved = false
        var consumed = false
        var dragging = false
        var holdTask: Runnable? = null
    }
    private val cursors = SparseArray<CursorPtr>()

    private class EditDrag(val startX: Float, val startY: Float) { var moved = false }
    private val editDrags = SparseArray<EditDrag>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 14f * density
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    // ---------- geometry ----------

    private fun bounds(c: Control): RectF {
        val w: Float
        val h: Float
        when (c) {
            is ButtonControl -> { w = c.widthDp * density; h = c.heightDp * density }
            is LayerToggleControl -> { w = c.widthDp * density; h = c.heightDp * density }
            is JoystickControl -> { w = c.sizeDp * density; h = w }
            else -> { w = 0f; h = 0f }
        }
        val left = c.x * width
        val top = c.y * height
        return RectF(left, top, left + w, top + h)
    }

    private fun visibleControls(): List<Control> =
        layout?.layers?.filter { it.visible }?.flatMap { it.controls } ?: emptyList()

    // ---------- drawing ----------

    override fun onDraw(canvas: Canvas) {
        for (c in visibleControls()) drawControl(canvas, c)
    }

    private fun drawControl(canvas: Canvas, c: Control) {
        val b = bounds(c)
        val active = c in pressed || c in toggled
        fill.color = Color.argb(if (active) 150 else 70, 255, 255, 255)
        stroke.color = if (editMode) Color.YELLOW else Color.argb(200, 255, 255, 255)
        when (c) {
            is JoystickControl -> {
                val r = b.width() / 2
                canvas.drawCircle(b.centerX(), b.centerY(), r, fill)
                canvas.drawCircle(b.centerX(), b.centerY(), r, stroke)
                val t = joyThumb[c]
                fill.color = Color.argb(170, 255, 255, 255)
                canvas.drawCircle(t?.x ?: b.centerX(), t?.y ?: b.centerY(), r * 0.4f, fill)
            }
            is ButtonControl -> drawBox(canvas, b, c.label)
            is LayerToggleControl -> drawBox(canvas, b, c.label)
        }
    }

    private fun drawBox(canvas: Canvas, b: RectF, label: String) {
        val r = 12f * density
        canvas.drawRoundRect(b, r, r, fill)
        canvas.drawRoundRect(b, r, r, stroke)
        canvas.drawText(label, b.centerX(), b.centerY() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
    }

    // ---------- touch ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                pointerDown(e.getPointerId(i), e.getX(i), e.getY(i), e.eventTime)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                pointerMove(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                pointerUp(e.getPointerId(i), e.eventTime)
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    private fun pointerDown(id: Int, x: Float, y: Float, time: Long) {
        val hit = visibleControls().lastOrNull { bounds(it).contains(x, y) }
        if (hit != null) {
            owners.put(id, hit)
            if (editMode) editDrags.put(id, EditDrag(x, y)) else press(hit, x, y)
            return
        }
        if (editMode) return

        // a second finger cancels any pending long-press of the first
        for (k in 0 until cursors.size()) cancelHold(cursors.valueAt(k))

        val p = CursorPtr(x, y, time)
        val task = Runnable {
            if (cursors.get(id) === p && !p.moved && !p.consumed) {
                p.dragging = true
                sink.mouseButton(MouseButton.LEFT, true)
            }
        }
        p.holdTask = task
        postDelayed(task, holdMs)
        cursors.put(id, p)
    }

    private fun pointerMove(id: Int, x: Float, y: Float) {
        val owned = owners.get(id)
        if (owned != null) {
            if (editMode) {
                val d = editDrags.get(id)
                if (d != null && !d.moved && hypot(x - d.startX, y - d.startY) > slop) d.moved = true
                if (d == null || d.moved) moveControl(owned, x, y)
            } else if (owned is JoystickControl) updateJoystick(owned, x, y)
            return
        }
        val p = cursors.get(id) ?: return
        if (!p.moved && hypot(x - p.startX, y - p.startY) > slop) {
            p.moved = true
            if (!p.dragging) cancelHold(p)
        }
        if (p.moved || p.dragging) {
            sink.mouseMoveRelative((x - p.lastX) * cursorSensitivity, (y - p.lastY) * cursorSensitivity)
        }
        p.lastX = x
        p.lastY = y
    }

    private fun pointerUp(id: Int, time: Long) {
        val owned = owners.get(id)
        if (owned != null) {
            owners.remove(id)
            if (editMode) {
                val d = editDrags.get(id)
                editDrags.remove(id)
                if (d != null && !d.moved) onControlTapped?.invoke(owned) else onLayoutEdited?.invoke()
            } else release(owned)
            return
        }
        val p = cursors.get(id) ?: return
        cancelHold(p)
        if (p.dragging) {
            sink.mouseButton(MouseButton.LEFT, false)
        } else if (!p.moved && !p.consumed && time - p.startTime < tapMaxMs) {
            val button = if (cursors.size() >= 2) {
                for (k in 0 until cursors.size()) cursors.valueAt(k).consumed = true
                MouseButton.RIGHT
            } else MouseButton.LEFT
            sink.mouseButton(button, true)
            sink.mouseButton(button, false)
        }
        cursors.remove(id)
    }

    private fun cancelHold(p: CursorPtr) {
        p.holdTask?.let { removeCallbacks(it) }
        p.holdTask = null
    }

    // ---------- control behaviour ----------

    private fun fire(a: Action, down: Boolean) {
        when (a.kind) {
            ActionKind.KEY -> sink.key(a.value, down)
            ActionKind.MOUSE -> sink.mouseButton(
                when (a.value) { "right" -> MouseButton.RIGHT; "middle" -> MouseButton.MIDDLE; else -> MouseButton.LEFT },
                down,
            )
            ActionKind.SCROLL -> if (down) sink.scroll(if (a.value == "up") 1 else -1)
        }
    }

    private fun press(c: Control, x: Float, y: Float) {
        when (c) {
            is ButtonControl -> {
                pressed.add(c)
                if (c.mode == PressMode.TOGGLE) {
                    if (toggled.remove(c)) fire(c.action, false) else { toggled.add(c); fire(c.action, true) }
                } else fire(c.action, true)
            }
            is LayerToggleControl -> {
                pressed.add(c)
                layout?.layers?.firstOrNull { it.id == c.target }?.let { it.visible = !it.visible }
            }
            is JoystickControl -> { pressed.add(c); updateJoystick(c, x, y) }
        }
        invalidate()
    }

    private fun release(c: Control) {
        pressed.remove(c)
        when (c) {
            is ButtonControl -> if (c.mode == PressMode.HOLD) fire(c.action, false)
            is JoystickControl -> { setJoystickKeys(c, emptySet()); joyThumb.remove(c) }
            else -> {}
        }
        invalidate()
    }

    private fun updateJoystick(c: JoystickControl, x: Float, y: Float) {
        val b = bounds(c)
        val dx = x - b.centerX()
        val dy = y - b.centerY()
        val r = b.width() / 2
        val dead = r * 0.35f
        val keys = HashSet<String>()
        if (dx > dead) keys.add(c.right)
        if (dx < -dead) keys.add(c.left)
        if (dy > dead) keys.add(c.down)
        if (dy < -dead) keys.add(c.up)
        setJoystickKeys(c, keys)

        val len = hypot(dx, dy)
        val k = if (len > r) r / len else 1f
        joyThumb[c] = PointF(b.centerX() + dx * k, b.centerY() + dy * k)
        invalidate()
    }

    private fun setJoystickKeys(c: JoystickControl, new: Set<String>) {
        val old = joyKeys[c] ?: emptySet()
        (old - new).forEach { sink.key(it, false) }
        (new - old).forEach { sink.key(it, true) }
        joyKeys[c] = new
    }

    private fun moveControl(c: Control, x: Float, y: Float) {
        if (width == 0 || height == 0) return
        val b = bounds(c)
        val maxX = (1f - b.width() / width).coerceAtLeast(0f)
        val maxY = (1f - b.height() / height).coerceAtLeast(0f)
        c.x = ((x - b.width() / 2) / width).coerceIn(0f, maxX)
        c.y = ((y - b.height() / 2) / height).coerceIn(0f, maxY)
        invalidate()
    }

    /** Lifts everything that is currently held so nothing gets stuck. */
    fun releaseAll() {
        if (!editMode) for (i in 0 until owners.size()) release(owners.valueAt(i))
        owners.clear()
        editDrags.clear()
        toggled.filterIsInstance<ButtonControl>().forEach { fire(it.action, false) }
        toggled.clear()
        for (i in 0 until cursors.size()) {
            val p = cursors.valueAt(i)
            cancelHold(p)
            if (p.dragging) sink.mouseButton(MouseButton.LEFT, false)
        }
        cursors.clear()
        pressed.clear()
        joyThumb.clear()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }
}
