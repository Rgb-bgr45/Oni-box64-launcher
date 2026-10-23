package dev.onilauncher

import java.io.File

/** Checks prerequisites and starts the game under box64. */
class Box64Launcher(private val paths: LauncherPaths) {

    val hasBox64: Boolean get() = paths.box64.isFile
    val hasRootfs: Boolean get() = paths.rootfs.isDirectory
    val hasGame: Boolean get() = paths.findGameExecutable() != null

    /** Problems that stop the game from starting. The launcher app itself works regardless. */
    fun preflight(): List<String> {
        val problems = mutableListOf<String>()
        if (!hasBox64) problems += "libbox64.so is missing (put an Android arm64 build in app/src/main/jniLibs/arm64-v8a/)"
        if (!hasRootfs) problems += "x86_64 rootfs not installed (use \"Install rootfs\")"
        if (!hasGame) problems += "Game files not imported (optional for testing the launcher)"
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

    /** Starts the process and streams combined stdout/stderr to [onLine] and logs/last-run.log. */
    fun start(onLine: (String) -> Unit, onExit: (Int) -> Unit) {
        val exe = paths.findGameExecutable() ?: error("Game executable not found")
        val pb = ProcessBuilder(paths.box64.absolutePath, exe.absolutePath)
            .directory(paths.gameDir)
            .redirectErrorStream(true)
        pb.environment().putAll(buildEnvironment())
        val logFile = File(paths.logsDir, "last-run.log")

        Thread {
            try {
                val proc = pb.start()
                logFile.bufferedWriter().use { w ->
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        w.appendLine(line)
                        w.flush()
                        onLine(line)
                    }
                }
                onExit(proc.waitFor())
            } catch (e: Exception) {
                onLine("Launch failed: ${e.message}")
                onExit(-1)
            }
        }.start()
    }
}
