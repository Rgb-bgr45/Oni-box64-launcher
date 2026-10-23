package dev.onilauncher.controls

import android.content.Context
import android.net.Uri
import java.io.File

/** Layout files live in files/controls/NAME.json. A default is copied from assets on first run. */
class LayoutStore(private val ctx: Context) {
    private val dir: File get() = File(ctx.filesDir, "controls").also { it.mkdirs() }

    fun ensureDefaults() {
        if (list().isNotEmpty()) return
        val text = ctx.assets.open("controls/oni_default.json").bufferedReader().use { it.readText() }
        File(dir, "oni_default.json").writeText(text)
    }

    fun list(): List<File> = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name } ?: emptyList()

    fun load(file: File): Layout = LayoutJson.parse(file.readText())

    fun save(file: File, layout: Layout) = file.writeText(LayoutJson.toJson(layout))

    /** Validates before saving, so a broken file never lands in the list. */
    fun importFrom(uri: Uri): File {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Could not read the selected file")
        val layout = LayoutJson.parse(text)
        val safe = layout.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "layout" }
        val target = File(dir, "$safe.json")
        target.writeText(text)
        return target
    }
}
