package dev.onilauncher

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Copies a game folder the user picked (Storage Access Framework tree URI)
 * into app storage. The launcher never downloads or bundles game files.
 */
class GameImporter(private val ctx: Context, private val paths: LauncherPaths) {

    /** Blocking; call from a background thread. Returns number of files copied. */
    fun import(treeUri: Uri, onProgress: (String) -> Unit): Int {
        val root = DocumentFile.fromTreeUri(ctx, treeUri)
            ?: throw IllegalArgumentException("Could not open the selected folder")
        paths.gameDir.deleteRecursively()
        return copyTree(root, paths.gameDir, onProgress)
    }

    private fun copyTree(src: DocumentFile, dst: File, onProgress: (String) -> Unit): Int {
        dst.mkdirs()
        var count = 0
        for (child in src.listFiles()) {
            val name = child.name ?: continue
            val target = File(dst, name)
            if (child.isDirectory) {
                count += copyTree(child, target, onProgress)
            } else if (child.isFile) {
                ctx.contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out, 1 shl 16) }
                }
                count++
                if (count % 50 == 0) onProgress("Copied $count files...")
            }
        }
        return count
    }
}
