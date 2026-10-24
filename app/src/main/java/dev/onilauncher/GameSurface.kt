package dev.onilauncher

import android.content.Context
import android.graphics.Paint
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * The surface the game will be drawn into. Until a display backend (X server / GL layer)
 * exists, it only shows a placeholder, which proves the screen path works.
 */
class GameSurface(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8899AA.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 16f * resources.displayMetrics.density
    }

    init { holder.addCallback(this) }

    override fun surfaceCreated(holder: SurfaceHolder) = drawPlaceholder(holder)
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = drawPlaceholder(holder)
    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    private fun drawPlaceholder(holder: SurfaceHolder) {
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(0xFF10151B.toInt())
            canvas.drawText("Game screen (display backend not implemented yet)", canvas.width / 2f, canvas.height / 2f, paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}
