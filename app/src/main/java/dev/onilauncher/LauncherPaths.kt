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

    /** Finds the Unity player executable: a "<Name>_Data" dir with a sibling "<Name>" file. */
    fun findGameExecutable(): File? {
        val dir = gameDir
        val dataDir = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.endsWith("_Data") }
            ?: return null
        val exe = File(dir, dataDir.name.removeSuffix("_Data"))
        return exe.takeIf { it.isFile }
    }
}
