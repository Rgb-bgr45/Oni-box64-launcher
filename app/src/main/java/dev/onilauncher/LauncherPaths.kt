package dev.onilauncher

import android.content.Context
import java.io.File

/** All filesystem locations used by the launcher, in one place. */
class LauncherPaths(private val ctx: Context) {
    /** box64 shipped as jniLibs/arm64-v8a/libbox64.so (see README). */
    val box64: File get() = File(ctx.applicationInfo.nativeLibraryDir, "libbox64.so")

    /** User-imported game files (never part of this repo). */
    val gameDir: File get() = File(ctx.filesDir, "games/oni").also { it.mkdirs() }

    /** x86_64 glibc rootfs that box64 uses for guest libraries. */
    val rootfs: File get() = File(ctx.filesDir, "runtime/rootfs")

    /** Redirected HOME so Unity saves/config stay inside the app. */
    val home: File get() = File(ctx.filesDir, "home").also { it.mkdirs() }

    /** Finds the Unity player executable: a "<Name>_Data" dir with a sibling "<Name>" file. */
    fun findGameExecutable(): File? {
        val dir = gameDir
        val dataDir = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.endsWith("_Data") }
            ?: return null
        val exe = File(dir, dataDir.name.removeSuffix("_Data"))
        return exe.takeIf { it.isFile }
    }
}
