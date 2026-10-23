package dev.onilauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var paths: LauncherPaths
    private lateinit var launcher: Box64Launcher
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView

    private val pickGame = 1001
    private val pickRootfs = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paths = LauncherPaths(this)
        launcher = Box64Launcher(paths)

        status = TextView(this).apply { textSize = 15f }
        log = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(log) }

        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

        @Suppress("DEPRECATION")
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(status)
            addView(button("Diagnostics (check that the launcher works)") { runDiagnostics() })
            addView(button("Select game folder (optional)") {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), pickGame)
            })
            addView(button("Install rootfs (x86_64 .tar/.tar.gz/.tar.xz)") {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                    pickRootfs,
                )
            })
            addView(button("Touch controls") { startActivity(Intent(this@MainActivity, ControlsActivity::class.java)) })
            addView(button("Launch game") { launch() })
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        refreshStatus()
    }

    private fun mark(ok: Boolean) = if (ok) "OK" else "missing"

    private fun refreshStatus() {
        status.text = buildString {
            appendLine("Launcher: running (game files are optional)")
            appendLine("box64: ${mark(launcher.hasBox64)}")
            appendLine("Rootfs: ${mark(launcher.hasRootfs)}")
            appendLine("Game files: ${mark(launcher.hasGame)}")
            append("Data folder: ${paths.dataRoot}")
            if (!paths.external) append("  (internal fallback)")
        }
    }

    private fun appendLog(line: String) = runOnUiThread {
        log.append(line + "\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun launch() {
        val problems = launcher.preflight()
        if (problems.isNotEmpty()) {
            appendLog("Cannot start the game yet:")
            problems.forEach { appendLog(" - $it") }
            return
        }
        appendLog("Starting...")
        launcher.start(::appendLog) { code -> appendLog("Exited with code $code") }
    }

    private fun runDiagnostics() {
        appendLog("Running diagnostics...")
        Thread {
            val report = Diagnostics(this, paths).run()
            appendLog(report)
            runOnUiThread { refreshStatus() }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            pickGame -> runInBackground("Importing game files...") {
                val n = GameImporter(this, paths).import(uri) { appendLog(it) }
                appendLog("Imported $n files")
            }
            pickRootfs -> runInBackground("Installing rootfs...") {
                val n = RuntimeInstaller(this, paths).install(uri) { appendLog(it) }
                appendLog("Extracted $n entries")
            }
        }
    }

    private fun runInBackground(message: String, work: () -> Unit) {
        appendLog(message)
        Thread {
            try { work() } catch (e: Exception) { appendLog("Failed: ${e.message}") }
            runOnUiThread { refreshStatus() }
        }.start()
    }
}
