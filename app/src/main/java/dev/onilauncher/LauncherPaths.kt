package dev.onilauncher

import android.content.Context
import java.io.File

/**
 * Where everything lives.
 *
 * dataRoot = Android/data/<applicationId>/files (reachable by the user) with an
 * internal fallback if external storage is unavailable.
 *
 * The rootfs stays in internal storage on purpose: it relies on symlinks, which
 * external app storage does not support.
 */
class LauncherPaths(private val ctx: Context) {
    private val externalRoot: File? = ctx.getExternalFilesDir(null)
    val external: Boolean = externalRoot != null
    val dataRoot: File = externalRoot ?: ctx.filesDir

    /** box64 shipped as jniLibs/arm64-v8a/libbox64.so (see README). */
    val box64: File get() = File(ctx.applicationInfo.nativeLibraryDir, "libbox64.so")

    /** User-imported game files (never part of this repo). */
    val gameDir: File get() = File(dataRoot, "games/oni").also { it.mkdirs() }

    /** Touch layout JSON files. */
    val controlsDir: File get() = File(dataRoot, "controls").also { it.mkdirs() }

    /** Redirected HOME so Unity saves/config stay in the data folder. */
    val home: File get() = File(dataRoot, "home").also { it.mkdirs() }

    val logsDir: File get() = File(dataRoot, "logs").also { it.mkdirs() }

    /** x86_64 glibc rootfs used by box64 for guest libraries (internal, needs symlinks). */
    val rootfs: File get() = File(ctx.filesDir, "runtime/rootfs")

    /**
     * Looks up to 3 folders deep for a Unity Linux build: a "<Name>_Data" folder next to
     * "<Name>" or "<Name>.x86_64". This also works if the user picked a parent folder.
     */
    fun scanGame(): GameScan {
        val root = gameDir
        var windowsDir: File? = null
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (data in children.filter { it.isDirectory && it.name.endsWith("_Data") }) {
                val name = data.name.removeSuffix("_Data")
                val exe = listOf(name, "$name.x86_64").map { File(dir, it) }.firstOrNull { it.isFile }
                if (exe != null) return GameScan.Found(exe)
                if (windowsDir == null && File(dir, "$name.exe").isFile) windowsDir = dir
            }
            if (depth < 3) children.filter { it.isDirectory }.forEach { queue.add(it to depth + 1) }
        }
        return windowsDir?.let { GameScan.WindowsOnly(it) }
            ?: GameScan.Missing(root.list()?.sorted()?.take(10) ?: emptyList())
    }

    fun findGameExecutable(): File? = (scanGame() as? GameScan.Found)?.exe

    fun gameStatusText(): String = when (val s = scanGame()) {
        is GameScan.Found -> "found (${s.exe.name})"
        is GameScan.WindowsOnly -> "only a Windows build found - the Linux build is needed"
        is GameScan.Missing ->
            if (s.topEntries.isEmpty()) "missing (folder is empty, the import copied nothing)"
            else "missing (no <Name>_Data + <Name> pair; folder holds: ${s.topEntries.joinToString()})"
    }
}

sealed class GameScan {
    data class Found(val exe: File) : GameScan()
    data class WindowsOnly(val dir: File) : GameScan()
    data class Missing(val topEntries: List<String>) : GameScan()
}
