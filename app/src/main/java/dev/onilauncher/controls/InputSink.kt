package dev.onilauncher.controls

/**
 * Where touch-control events go. The real implementation will forward them to
 * the display/input bridge of the X server once that exists. Until then the
 * controls screen uses LoggingInputSink so layouts can be tested on their own.
 */
interface InputSink {
    fun key(name: String, down: Boolean)
    fun mouseButton(button: MouseButton, down: Boolean)
    fun mouseMoveRelative(dx: Float, dy: Float)
    /** Positive = scroll up. */
    fun scroll(steps: Int)
}

class LoggingInputSink(private val log: (String) -> Unit) : InputSink {
    override fun key(name: String, down: Boolean) = log("key $name ${if (down) "down" else "up"}")
    override fun mouseButton(button: MouseButton, down: Boolean) =
        log("mouse $button ${if (down) "down" else "up"}")
    override fun mouseMoveRelative(dx: Float, dy: Float) { /* too noisy to log */ }
    override fun scroll(steps: Int) = log("scroll $steps")
}
