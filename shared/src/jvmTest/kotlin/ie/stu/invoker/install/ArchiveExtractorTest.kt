package ie.stu.invoker.install

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchiveExtractorTest {

    private lateinit var tmpDir: Path

    @BeforeTest
    fun setUp() {
        tmpDir = Files.createTempDirectory("invoker-archive-test-")
    }

    @AfterTest
    fun tearDown() {
        Files.walk(tmpDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `extractZip lays out files from archive`() {
        val zip = tmpDir.resolve("source.zip")
        buildZip(zip, mapOf(
            "mage-client/lib/foo.jar" to "fake-jar-bytes",
            "mage-server/lib/bar.jar" to "another-jar",
            "readme.txt" to "Hello XMage",
        ))
        val dest = Files.createDirectory(tmpDir.resolve("dest"))

        ArchiveExtractor.extractZip(zip, dest)

        assertEquals("fake-jar-bytes", dest.resolve("mage-client/lib/foo.jar").readText())
        assertEquals("another-jar", dest.resolve("mage-server/lib/bar.jar").readText())
        assertEquals("Hello XMage", dest.resolve("readme.txt").readText())
    }

    @Test
    fun `extractXMageZip preserves existing images, gameLogs, and backgrounds dirs`() {
        val zip = tmpDir.resolve("xmage-update.zip")
        // Update archive contains updated lib + an "images" entry that should be skipped because
        // the destination already has user data in there.
        buildZip(zip, mapOf(
            "mage-client/lib/new.jar" to "new-version",
            "images/should-not-overwrite.png" to "from-zip",
        ))

        val dest = Files.createDirectory(tmpDir.resolve("xmage"))
        val preservedImage = dest.resolve("images/card-art.png")
        Files.createDirectories(preservedImage.parent)
        preservedImage.writeText("user-card-art")

        ArchiveExtractor.extractXMageZip(zip, dest)

        assertEquals(
            "user-card-art",
            preservedImage.readText(),
            "Pre-existing images/ files must survive a re-extract over the dir",
        )
        assertEquals("new-version", dest.resolve("mage-client/lib/new.jar").readText())
        // The image from the zip should NOT have been written, because images/ pre-existed.
        assertTrue(
            !dest.resolve("images/should-not-overwrite.png").exists(),
            "Files inside a preserved directory must be skipped entirely",
        )
    }

    /** Writes a fresh zip containing the given `path → text content` map. */
    private fun buildZip(target: Path, entries: Map<String, String>) {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        Files.write(target, baos.toByteArray())
    }
}
