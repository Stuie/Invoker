package ie.stu.invoker.install

import ie.stu.invoker.process.AppLog
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipInputStream

/** Preserved when re-extracting XMage so card images / logs / backgrounds survive an update. */
private val xmagePreserveDirs = setOf("images", "gameLogs", "backgrounds")

/**
 * Kept (never deleted) when wiping an existing XMage install ahead of a fresh extract. Mirrors the
 * Java launcher's `removeXMageFiles` filter: the big image/log/background caches plus user deck
 * files. Everything else — crucially the version-stamped `mage-...-x.y.z.jar` files — is removed so
 * old and new jars can't coexist on the wildcard `lib` classpath.
 */
private val xmagePreserveNames = setOf("images", "gameLogs", "backgrounds", "mageclient.log", "mageserver.log")

private fun isXMagePreserved(name: String): Boolean = name in xmagePreserveNames || name.endsWith(".dck")

object ArchiveExtractor {

    fun extractZip(archive: Path, destination: Path, preserveExistingDirs: Set<String> = emptySet()) {
        Files.createDirectories(destination)
        Files.newInputStream(archive).use { fis ->
            ZipInputStream(BufferedInputStream(fis)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val target = resolveSafely(destination, entry.name)
                    val topLevel = entry.name.substringBefore('/')
                    val skip = topLevel in preserveExistingDirs && Files.exists(destination.resolve(topLevel))
                    if (!skip) {
                        if (entry.isDirectory) {
                            Files.createDirectories(target)
                        } else {
                            Files.createDirectories(target.parent)
                            Files.newOutputStream(target).use { zis.copyTo(it) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    /**
     * Re-install XMage: wipe the previous install (except the preserved caches/decks), then extract
     * fresh. The wipe is what prevents version-stamped jars from accumulating across updates — the
     * bug that put 1.4.58/59/60 jars on the classpath at once and crashed the server with
     * `NoSuchFieldError` while loading cards.
     */
    fun extractXMageZip(archive: Path, destination: Path) {
        if (Files.isDirectory(destination)) {
            AppLog.i("Cleaning previous XMage install at $destination (preserving images/logs/decks)")
            val removed = cleanXMageFiles(destination)
            AppLog.i("Removed $removed stale file(s) before extract")
        }
        extractZip(archive, destination, xmagePreserveDirs)
    }

    /**
     * Recursively delete files under [dir], skipping anything matched by [isXMagePreserved] (by name,
     * at any depth — a preserved directory is left untouched entirely). Directories themselves are
     * left in place; the extract repopulates them. Returns the number of files deleted.
     */
    private fun cleanXMageFiles(dir: Path): Int {
        var count = 0
        Files.newDirectoryStream(dir).use { stream ->
            for (child in stream) {
                val name = child.fileName.toString()
                if (isXMagePreserved(name)) continue
                count += if (Files.isDirectory(child)) {
                    cleanXMageFiles(child)
                } else {
                    if (runCatching { Files.deleteIfExists(child) }.getOrElse {
                            AppLog.w("Could not delete $child: ${it.message}"); false
                        }) 1 else 0
                }
            }
        }
        return count
    }

    fun extractTarGz(archive: Path, destination: Path) {
        Files.createDirectories(destination)
        Files.newInputStream(archive).use { fis ->
            GzipCompressorInputStream(BufferedInputStream(fis)).use { gzis ->
                TarArchiveInputStream(gzis).use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        val target = resolveSafely(destination, entry.name)
                        when {
                            entry.isDirectory -> Files.createDirectories(target)
                            entry.isSymbolicLink -> {
                                Files.createDirectories(target.parent)
                                Files.deleteIfExists(target)
                                try {
                                    Files.createSymbolicLink(target, target.fileSystem.getPath(entry.linkName))
                                } catch (_: UnsupportedOperationException) {
                                    // fall back to file write of link name
                                }
                            }
                            else -> {
                                Files.createDirectories(target.parent)
                                Files.newOutputStream(target).use { tar.copyTo(it) }
                                applyPosixMode(target, entry.mode)
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }

    private fun resolveSafely(base: Path, name: String): Path {
        val normalized = base.resolve(name).normalize()
        if (!normalized.startsWith(base.normalize())) {
            throw SecurityException("Archive entry escapes destination: $name")
        }
        return normalized
    }

    private fun applyPosixMode(file: Path, mode: Int) {
        if (mode <= 0) return
        try {
            val perms = mutableSetOf<PosixFilePermission>()
            if (mode and 0b100_000_000 != 0) perms += PosixFilePermission.OWNER_READ
            if (mode and 0b010_000_000 != 0) perms += PosixFilePermission.OWNER_WRITE
            if (mode and 0b001_000_000 != 0) perms += PosixFilePermission.OWNER_EXECUTE
            if (mode and 0b000_100_000 != 0) perms += PosixFilePermission.GROUP_READ
            if (mode and 0b000_010_000 != 0) perms += PosixFilePermission.GROUP_WRITE
            if (mode and 0b000_001_000 != 0) perms += PosixFilePermission.GROUP_EXECUTE
            if (mode and 0b000_000_100 != 0) perms += PosixFilePermission.OTHERS_READ
            if (mode and 0b000_000_010 != 0) perms += PosixFilePermission.OTHERS_WRITE
            if (mode and 0b000_000_001 != 0) perms += PosixFilePermission.OTHERS_EXECUTE
            Files.setPosixFilePermissions(file, perms)
        } catch (_: UnsupportedOperationException) {
            // Windows: ignore — no POSIX perms.
        }
    }
}
