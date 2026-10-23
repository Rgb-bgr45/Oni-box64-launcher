package dev.onilauncher

/**
 * Starting-point box64 settings. These are NOT tuned values: expect to
 * change them per device after reading box64 logs and docs.
 */
object LauncherConfig {
    val box64Env: Map<String, String> = mapOf(
        "BOX64_LOG" to "1",
        "BOX64_DYNAREC" to "1",
        "BOX64_DYNAREC_BIGBLOCK" to "1",
        "BOX64_DYNAREC_STRONGMEM" to "1",
    )

    /** Placeholder: a display backend (X server or equivalent) is not implemented yet. */
    const val DISPLAY = ":0"
}
