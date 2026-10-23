package dev.onilauncher.controls

enum class ActionKind { KEY, MOUSE, SCROLL }
enum class PressMode { HOLD, TOGGLE }
enum class MouseButton { LEFT, RIGHT, MIDDLE }

/** KEY: value is a key name (see KeyNames). MOUSE: left/right/middle. SCROLL: up/down. */
data class Action(val kind: ActionKind, val value: String)

/** x,y are fractions (0..1) of the screen for the control's top-left corner. Sizes are in dp. */
abstract class Control {
    abstract val id: String
    abstract var x: Float
    abstract var y: Float
}

class ButtonControl(
    override val id: String, override var x: Float, override var y: Float,
    var widthDp: Float, var heightDp: Float,
    var label: String, var action: Action, var mode: PressMode,
) : Control()

class JoystickControl(
    override val id: String, override var x: Float, override var y: Float,
    var sizeDp: Float,
    var up: String, var down: String, var left: String, var right: String,
) : Control()

/** Shows/hides another layer when tapped. */
class LayerToggleControl(
    override val id: String, override var x: Float, override var y: Float,
    var widthDp: Float, var heightDp: Float,
    var label: String, var target: String,
) : Control()

class Layer(val id: String, var visible: Boolean, val controls: MutableList<Control>)

class Layout(val version: Int, val name: String, val layers: MutableList<Layer>)

object KeyNames {
    val valid: Set<String> = buildSet {
        addAll(('A'..'Z').map { it.toString() })
        addAll(('0'..'9').map { it.toString() })
        addAll((1..12).map { "F$it" })
        addAll(listOf("SPACE", "ESC", "SHIFT", "CTRL", "ALT", "TAB", "ENTER",
            "BACKSPACE", "DELETE", "UP", "DOWN", "LEFT", "RIGHT"))
    }
}
