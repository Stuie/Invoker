package ie.stu.invoker.install

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipInputStream

/** Preserved when re-extracting XMage so card images / logs / backgrounds survive an update. */
private val xmagePreserveDirs = setOf("images", "gameLogs", "backgrounds")

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

    fun extractXMageZip(archive: Path, destination: Path) =
        extractZip(archive, destination, xmagePreserveDirs)

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
