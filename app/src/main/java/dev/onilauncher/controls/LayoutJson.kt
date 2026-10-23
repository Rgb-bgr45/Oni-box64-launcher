package dev.onilauncher.controls

import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes the layout JSON described in docs/CONTROLS_FORMAT.md. */
object LayoutJson {

    fun parse(text: String): Layout {
        val root = JSONObject(text)
        val version = root.optInt("version", 1)
        require(version == 1) { "Unsupported layout version: $version" }

        val layersJson = root.getJSONArray("layers")
        val layers = ArrayList<Layer>()
        val controlIds = HashSet<String>()
        for (i in 0 until layersJson.length()) {
            val lj = layersJson.getJSONObject(i)
            val cj = lj.getJSONArray("controls")
            val controls = ArrayList<Control>()
            for (k in 0 until cj.length()) {
                val c = parseControl(cj.getJSONObject(k))
                require(controlIds.add(c.id)) { "Duplicate control id: ${c.id}" }
                controls.add(c)
            }
            layers.add(Layer(lj.getString("id"), lj.optBoolean("visible", true), controls))
        }
        val layerIds = layers.map { it.id }.toSet()
        require(layerIds.size == layers.size) { "Duplicate layer id" }
        layers.flatMap { it.controls }.filterIsInstance<LayerToggleControl>().forEach {
            require(it.target in layerIds) { "Control '${it.id}' targets unknown layer '${it.target}'" }
        }
        return Layout(version, root.optString("name", "Unnamed"), layers)
    }

    private fun parseControl(o: JSONObject): Control {
        val id = o.getString("id")
        val x = o.getDouble("x").toFloat()
        val y = o.getDouble("y").toFloat()
        require(x in 0f..1f && y in 0f..1f) { "Control '$id': x and y must be between 0 and 1" }
        return when (val type = o.getString("type")) {
            "button" -> ButtonControl(
                id, x, y,
                o.optDouble("w", 56.0).toFloat(), o.optDouble("h", 48.0).toFloat(),
                o.optString("label", id),
                parseAction(id, o.getJSONObject("action")),
                if (o.optString("mode", "hold") == "toggle") PressMode.TOGGLE else PressMode.HOLD,
            )
            "joystick" -> {
                val k = o.getJSONObject("keys")
                JoystickControl(
                    id, x, y, o.optDouble("size", 140.0).toFloat(),
                    key(id, k, "up"), key(id, k, "down"), key(id, k, "left"), key(id, k, "right"),
                )
            }
            "layerToggle" -> LayerToggleControl(
                id, x, y,
                o.optDouble("w", 56.0).toFloat(), o.optDouble("h", 48.0).toFloat(),
                o.optString("label", id), o.getString("target"),
            )
            else -> throw IllegalArgumentException("Control '$id': unknown type '$type'")
        }
    }

    private fun key(id: String, keys: JSONObject, dir: String): String =
        keyName(id, keys.getString(dir))

    /** Normalizes and validates a key name; throws IllegalArgumentException if unknown. */
    fun keyName(id: String, raw: String): String {
        val name = raw.trim().uppercase()
        require(name in KeyNames.valid) { "Control '$id': unknown key '$name'" }
        return name
    }

    private fun parseAction(id: String, a: JSONObject): Action =
        makeAction(id, a.getString("kind"), a.getString("value"))

    /** Validates an action typed by the user or read from JSON. */
    fun makeAction(id: String, kindRaw: String, raw: String): Action {
        val kind = when (val k = kindRaw.trim().lowercase()) {
            "key" -> ActionKind.KEY
            "mouse" -> ActionKind.MOUSE
            "scroll" -> ActionKind.SCROLL
            else -> throw IllegalArgumentException("Control '$id': unknown action kind '$k'")
        }
        val value = when (kind) {
            ActionKind.KEY -> keyName(id, raw)
            ActionKind.MOUSE -> raw.trim().lowercase().also {
                require(it in setOf("left", "right", "middle")) { "Control '$id': unknown mouse button '$it'" }
            }
            ActionKind.SCROLL -> raw.trim().lowercase().also {
                require(it in setOf("up", "down")) { "Control '$id': scroll must be up or down" }
            }
        }
        return Action(kind, value)
    }

    fun toJson(layout: Layout): String {
        val layers = JSONArray()
        for (l in layout.layers) {
            val controls = JSONArray()
            for (c in l.controls) controls.put(controlToJson(c))
            layers.put(JSONObject().put("id", l.id).put("visible", l.visible).put("controls", controls))
        }
        return JSONObject()
            .put("version", layout.version).put("name", layout.name).put("layers", layers)
            .toString(2)
    }

    private fun controlToJson(c: Control): JSONObject {
        val o = JSONObject().put("id", c.id).put("x", c.x.toDouble()).put("y", c.y.toDouble())
        when (c) {
            is ButtonControl -> o.put("type", "button").put("w", c.widthDp.toDouble())
                .put("h", c.heightDp.toDouble()).put("label", c.label)
                .put("mode", if (c.mode == PressMode.TOGGLE) "toggle" else "hold")
                .put("action", JSONObject().put("kind", c.action.kind.name.lowercase()).put("value", c.action.value))
            is JoystickControl -> o.put("type", "joystick").put("size", c.sizeDp.toDouble())
                .put("keys", JSONObject().put("up", c.up).put("down", c.down).put("left", c.left).put("right", c.right))
            is LayerToggleControl -> o.put("type", "layerToggle").put("w", c.widthDp.toDouble())
                .put("h", c.heightDp.toDouble()).put("label", c.label).put("target", c.target)
        }
        return o
    }
}
