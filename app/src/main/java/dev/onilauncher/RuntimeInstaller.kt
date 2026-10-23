package dev.onilauncher

import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Extracts an x86_64 glibc rootfs archive (.tar, .tar.gz, .tar.xz) chosen by the user
 * into files/runtime/rootfs. No rootfs is bundled or downloaded by the app.
 *
 * Limitation: absolute symlinks (e.g. /usr/lib/x) would point at the Android root, not
 * the rootfs, so prefer an archive whose symlinks are relative.
 */
class RuntimeInstaller(private val ctx: Context, private val paths: LauncherPaths) {

    fun install(uri: Uri, onProgress: (String) -> Unit): Int {
        val raw = BufferedInputStream(
            ctx.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open file"),
            1 shl 16,
        )
        val magic = ByteArray(6)
        raw.mark(8)
        val n = raw.read(magic)
        raw.reset()

        val stream: InputStream = when {
            n >= 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> GzipCompressorInputStream(raw)
            n >= 6 && magic[0] == 0xFD.toByte() && magic[1] == 0x37.toByte() &&
                magic[2] == 0x7A.toByte() && magic[3] == 0x58.toByte() -> XZCompressorInputStream(raw)
            else -> raw
        }

        val rootDir = paths.rootfs
        rootDir.deleteRecursively()
        rootDir.mkdirs()
        val root = rootDir.toPath().toAbsolutePath().normalize()

        var count = 0
        TarArchiveInputStream(stream).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                val name = entry.name.removePrefix("./")
                if (name.isNotBlank()) {
                    val target = root.resolve(name).normalize()
                    require(target.startsWith(root)) { "Unsafe path in archive: $name" }

                    when {
                        entry.isDirectory -> Files.createDirectories(target)
                        entry.isSymbolicLink -> {
                            Files.createDirectories(target.parent)
                            Files.deleteIfExists(target)
                            Files.createSymbolicLink(target, Paths.get(entry.linkName))
                        }
                        entry.isLink -> {
                            val src = root.resolve(entry.linkName.removePrefix("./")).normalize()
                            if (src.startsWith(root) && Files.exists(src)) {
                                Files.createDirectories(target.parent)
                                Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                        entry.isFile -> {
                            Files.createDirectories(target.parent)
                            Files.newOutputStream(target).use { out -> tar.copyTo(out, 1 shl 16) }
                            if (entry.mode and 0b001001001 != 0) target.toFile().setExecutable(true, false)
                        }
                    }
                    count++
                    if (count % 500 == 0) onProgress("Extracted $count entries...")
                }
                entry = tar.nextTarEntry
            }
        }
        return count
    }
}
