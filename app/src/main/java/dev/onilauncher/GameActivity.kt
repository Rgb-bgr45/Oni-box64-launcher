package dev.onilauncher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.onilauncher.controls.ControlOverlayView
import dev.onilauncher.controls.LayoutStore
import dev.onilauncher.controls.LoggingInputSink

/**
 * Full-screen game screen opened by Launch: game surface, touch controls on top and a live log.
 * Starts the real game when everything is installed, otherwise the test mode.
 */
class GameActivity : Activity() {
    private lateinit var launcher: Box64Launcher
    private lateinit var ctrlOverlay: ControlOverlayView
    private lateinit var logView: TextView
    private val lines = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val paths = LauncherPaths(this)
        launcher = Box64Launcher(paths)

        ctrlOverlay = ControlOverlayView(this, LoggingInputSink(::appendLog))
        val store = LayoutStore(this, paths)
        store.ensureDefaults()
        try {
            store.list().firstOrNull()?.let { ctrlOverlay.layout = store.load(it) }
        } catch (e: Exception) {
            appendLog("Layout error: ${e.message}")
        }

        val density = resources.displayMetrics.density
        logView = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x66000000)
            setPadding(12, 6, 12, 6)
        }

        fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            textSize = 11f
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { onClick() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(smallButton("Log") {
                logView.visibility = if (logView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            })
            addView(smallButton("Controls") {
                if (ctrlOverlay.visibility == View.VISIBLE) {
                    ctrlOverlay.releaseAll()
                    ctrlOverlay.visibility = View.GONE
                } else ctrlOverlay.visibility = View.VISIBLE
            })
            addView(smallButton("Exit") { finish() })
        }

        setContentView(FrameLayout(this).apply {
            addView(GameSurface(context), FrameLayout.LayoutParams(-1, -1))
            addView(ctrlOverlay, FrameLayout.LayoutParams(-1, -1))
            addView(logView, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        })

        if (launcher.canLaunchGame) {
            appendLog("Starting the game...")
            launcher.start(::appendLog) { code -> appendLog("Game exited with code $code") }
        } else {
            appendLog("Game is not ready, running the launcher test:")
            launcher.preflight().forEach { appendLog(" - $it") }
            launcher.startTest(::appendLog) { code -> appendLog("Test finished, exit code $code") }
        }
    }

    private fun appendLog(line: String) = runOnUiThread {
        line.lines().forEach { lines.addLast(it) }
        while (lines.size > 14) lines.removeFirst()
        logView.text = lines.joinToString("\n")
    }

    override fun onDestroy() {
        ctrlOverlay.releaseAll()
        launcher.stop()
        super.onDestroy()
    }
}
