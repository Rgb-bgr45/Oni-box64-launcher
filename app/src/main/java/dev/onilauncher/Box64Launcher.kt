package dev.onilauncher

import java.io.File

/** Checks prerequisites and starts the game (or a test run) under box64. */
class Box64Launcher(private val paths: LauncherPaths) {

    @Volatile private var process: Process? = null

    /** Stops a running game or test process. */
    fun stop() { process?.destroy() }

    val hasBox64: Boolean get() = paths.box64.isFile
    val hasRootfs: Boolean get() = paths.rootfs.isDirectory
    val hasGame: Boolean get() = paths.findGameExecutable() != null

    /** True when the real game can be started. Otherwise Launch runs the test mode. */
    val canLaunchGame: Boolean get() = hasBox64 && hasRootfs && hasGame

    fun preflight(): List<String> {
        val problems = mutableListOf<String>()
        if (!hasBox64) problems += "libbox64.so is missing (put an Android arm64 build in app/src/main/jniLibs/arm64-v8a/)"
        if (!hasRootfs) problems += "x86_64 rootfs not installed (use \"Install rootfs\")"
        if (!hasGame) problems += "Game files: ${paths.gameStatusText()}"
        return problems
    }

    fun buildEnvironment(): Map<String, String> {
        val rootfs = paths.rootfs.absolutePath
        val exe = paths.findGameExecutable()
        val libPaths = buildList {
            add("$rootfs/lib/x86_64-linux-gnu")
            add("$rootfs/usr/lib/x86_64-linux-gnu")
            if (exe != null) {
                val name = exe.name.substringBefore('.')
                add("${exe.parentFile?.absolutePath}/${name}_Data/Plugins")
            }
        }.joinToString(":")

        return buildMap {
            putAll(LauncherConfig.box64Env)
            put("HOME", paths.home.absolutePath)
            put("DISPLAY", LauncherConfig.DISPLAY)
            put("BOX64_LD_LIBRARY_PATH", libPaths)
            put("BOX64_PATH", (exe?.parentFile ?: paths.gameDir).absolutePath)
        }
    }

    /** Starts the game. Call only when [canLaunchGame] is true. */
    fun start(onLine: (String) -> Unit, onExit: (Int) -> Unit) {
        val exe = paths.findGameExecutable() ?: error("Game executable not found")
        run(listOf(paths.box64.absolutePath, exe.absolutePath), exe.parentFile ?: paths.gameDir,
            "last-run.log", onLine, onExit)
    }

    /**
     * Test mode: needs no game files. Prints what a real launch would do, then runs
     * `box64 -v` if box64 exists so you can see the binary starts on this device.
     */
    fun startTest(onLine: (String) -> Unit, onExit: (Int) -> Unit) {
        onLine("== Test mode (game files not required) ==")
        onLine("box64:  ${if (hasBox64) "found" else "MISSING"}  (${paths.box64})")
        onLine("rootfs: ${if (hasRootfs) "found" else "not installed (box64 will start, guest libraries won't be found)"}")
        onLine("game:   ${paths.gameStatusText()}")
        val env = buildEnvironment()
        onLine("HOME=${env["HOME"]}")
        onLine("DISPLAY=${env["DISPLAY"]} (placeholder, no display backend yet)")
        if (!hasBox64) {
            onLine("[FAIL] Nothing to run: libbox64.so is missing. Launcher itself works.")
            onExit(-1)
            return
        }
        onLine("Running: ${paths.box64.name} -v")
        run(listOf(paths.box64.absolutePath, "-v"), paths.gameDir, "last-test.log", onLine, onExit)
    }

    private fun run(
        command: List<String>, workDir: File, logName: String,
        onLine: (String) -> Unit, onExit: (Int) -> Unit,
    ) {
        val pb = ProcessBuilder(command).directory(workDir).redirectErrorStream(true)
        pb.environment().putAll(buildEnvironment())
        val logFile = File(paths.logsDir, logName)

        Thread {
            try {
                val proc = pb.start()
                process = proc
                logFile.bufferedWriter().use { w ->
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        w.appendLine(line)
                        w.flush()
                        onLine(line)
                    }
                }
                onExit(proc.waitFor())
            } catch (e: Exception) {
                onLine("[FAIL] Could not start: ${e.message}")
                onExit(-1)
            }
        }.start()
    }
}
