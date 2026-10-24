package dev.onilauncher

import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

/** One-shot self test so you can check the launcher works before any game is installed. */
class Diagnostics(private val ctx: Context, private val paths: LauncherPaths) {

    /** Blocking (may run box64 -v for up to 5 s); call from a background thread. */
    fun run(): String {
        val report = buildString {
            val version = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            } catch (e: Exception) { "?" }
            appendLine("ONI Launcher $version")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("64-bit capable: ${Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()}")
            appendLine("Data folder: ${paths.dataRoot} (${if (paths.external) "external app folder" else "INTERNAL fallback"})")
            appendLine("Free space: ${paths.dataRoot.usableSpace / 1_000_000} MB")
            appendLine("Rootfs folder: ${paths.rootfs} (${if (paths.rootfs.isDirectory) "present" else "missing"})")
            appendLine("Game folder: ${paths.gameDir}\nGame files: ${paths.gameStatusText()} (optional)")
            appendLine("Control layouts: ${File(paths.dataRoot, "controls").listFiles { f -> f.extension == "json" }?.size ?: 0}")
            appendLine(box64Report())
        }
        try { File(paths.logsDir, "diagnostics.txt").writeText(report) } catch (_: Exception) {}
        return report
    }

    private fun box64Report(): String {
        val bin = paths.box64
        if (!bin.isFile) return "box64: missing (${bin})"
        return try {
            val proc = ProcessBuilder(bin.absolutePath, "-v").redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = Thread { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            reader.start()
            val finished = proc.waitFor(5, TimeUnit.SECONDS)
            if (!finished) proc.destroyForcibly()
            reader.join(1000)
            "box64: present, ${if (finished) "exit ${proc.exitValue()}" else "timed out"}\n${out.toString().trim()}"
        } catch (e: Exception) {
            "box64: present but could not run: ${e.message}"
        }
    }
}
