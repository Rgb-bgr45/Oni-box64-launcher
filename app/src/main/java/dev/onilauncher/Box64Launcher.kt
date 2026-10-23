package dev.onilauncher

import java.io.File

/** Checks prerequisites and starts the game under box64. */
class Box64Launcher(private val paths: LauncherPaths, private val filesDir: File) {

    /** Returns human-readable problems; empty list means ready to try. */
    fun preflight(): List<String> {
        val problems = mutableListOf<String>()
        if (!paths.box64.isFile) problems += "libbox64.so is missing (put an Android arm64 build in app/src/main/jniLibs/arm64-v8a/)"
        if (!paths.rootfs.isDirectory) problems += "x86_64 rootfs not installed (use "Install rootfs")"
        if (paths.findGameExecutable() == null) problems += "Game files not imported (need <Name> and <Name>_Data from the Linux build)"
        return problems
    }

    fun buildEnvironment(): Map<String, String> {
        val rootfs = paths.rootfs.absolutePath
        val libPaths = listOf(
            "$rootfs/lib/x86_64-linux-gnu",
            "$rootfs/usr/lib/x86_64-linux-gnu",
            "${paths.gameDir.absolutePath}/OxygenNotIncluded_Data/Plugins",
        ).joinToString(":")

        return buildMap {
            putAll(LauncherConfig.box64Env)
            put("HOME", paths.home.absolutePath)
            put("DISPLAY", LauncherConfig.DISPLAY)
            put("BOX64_LD_LIBRARY_PATH", libPaths)
            put("BOX64_PATH", paths.gameDir.absolutePath)
        }
    }

    /** Starts the process and streams combined stdout/stderr to [onLine]. */
    fun start(onLine: (String) -> Unit, onExit: (Int) -> Unit) {
        val exe = paths.findGameExecutable() ?: error("Game executable not found")
        val pb = ProcessBuilder(paths.box64.absolutePath, exe.absolutePath)
            .directory(paths.gameDir)
            .redirectErrorStream(true)
        pb.environment().putAll(buildEnvironment())

        Thread {
            try {
                val proc = pb.start()
                proc.inputStream.bufferedReader().forEachLine(onLine)
                onExit(proc.waitFor())
            } catch (e: Exception) {
                onLine("Launch failed: ${e.message}")
                onExit(-1)
            }
        }.start()
    }
}
