package icons

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Generates platform-specific app icons from a single high-resolution PNG source.
 *
 * Outputs (next to the source):
 *  - `icon.png`  — single 512×512 PNG for Linux jpackage builds.
 *  - `icon.ico`  — multi-resolution Windows icon (16/24/32/48/64/128/256), modern PNG-embedded variant.
 *  - `icon.icns` — macOS icon bundle with 128/256/512/1024 entries (ic07/08/09/10).
 *
 * Implemented in pure Kotlin/JVM so no external tools (ImageMagick, Inkscape, iconutil) are
 * required. The encoders use the format outlines from:
 *  - ICO: https://en.wikipedia.org/wiki/ICO_(file_format)
 *  - ICNS: https://en.wikipedia.org/wiki/Apple_Icon_Image_format
 *
 * Both ICO (Windows Vista+) and ICNS (macOS 10.7+) accept raw PNG payloads, so we encode each
 * resolution as PNG and assemble the container around them — no BMP / RLE / packed-bits paths.
 */
object IconGenerator {

    private val icoSizes = intArrayOf(16, 24, 32, 48, 64, 128, 256)
    private val icnsEntries = listOf(
        IcnsEntry("ic07", 128),
        IcnsEntry("ic08", 256),
        IcnsEntry("ic09", 512),
        IcnsEntry("ic10", 1024),
    )

    fun generate(source: File, outDir: File) {
        require(source.exists()) { "Icon source not found: $source" }
        outDir.mkdirs()
        val src: BufferedImage = ImageIO.read(source)
            ?: error("Couldn't read $source as an image")

        // Linux: single 512px PNG.
        ImageIO.write(resize(src, 512), "png", File(outDir, "icon.png"))

        // Windows: multi-res ICO with PNG-embedded entries.
        writeIco(src, File(outDir, "icon.ico"))

        // macOS: ICNS with retina-friendly sizes.
        writeIcns(src, File(outDir, "icon.icns"))
    }

    // ── Resizer ──────────────────────────────────────────────────────────────

    private fun resize(src: BufferedImage, size: Int): BufferedImage {
        val dst = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = dst.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(src, 0, 0, size, size, null)
        } finally {
            g.dispose()
        }
        return dst
    }

    private fun pngBytes(image: BufferedImage): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    // ── ICO encoder ──────────────────────────────────────────────────────────

    private fun writeIco(src: BufferedImage, target: File) {
        // Encode each size as PNG bytes upfront so we can compute offsets in one pass.
        val pngs: List<Pair<Int, ByteArray>> = icoSizes.map { size ->
            size to pngBytes(resize(src, size))
        }

        ByteArrayOutputStream().use { body ->
            DataOutputStream(body).use { out ->
                // ICONDIR header: reserved(2) + type(2) + count(2), all little-endian.
                out.writeLEShort(0)               // reserved
                out.writeLEShort(1)               // type: 1 = icon
                out.writeLEShort(pngs.size)       // count

                // Each directory entry is 16 bytes; image data starts after the header + dir.
                var offset = 6 + pngs.size * 16
                for ((size, png) in pngs) {
                    out.writeByte(if (size >= 256) 0 else size)  // width  (0 ⇒ 256)
                    out.writeByte(if (size >= 256) 0 else size)  // height (0 ⇒ 256)
                    out.writeByte(0)              // colors in palette (0 = no palette)
                    out.writeByte(0)              // reserved
                    out.writeLEShort(1)           // color planes
                    out.writeLEShort(32)          // bits per pixel
                    out.writeLEInt(png.size)      // image data size
                    out.writeLEInt(offset)        // image data offset
                    offset += png.size
                }

                // Image payloads follow, in directory order.
                for ((_, png) in pngs) out.write(png)
            }
            target.writeBytes(body.toByteArray())
        }
    }

    // ── ICNS encoder ─────────────────────────────────────────────────────────

    private data class IcnsEntry(val type: String, val size: Int)

    private fun writeIcns(src: BufferedImage, target: File) {
        val chunks: List<Pair<String, ByteArray>> = icnsEntries.map { entry ->
            entry.type to pngBytes(resize(src, entry.size))
        }

        ByteArrayOutputStream().use { body ->
            DataOutputStream(body).use { out ->
                // Magic + total file size: 8-byte header + sum(chunk headers + payloads).
                val totalSize = 8 + chunks.sumOf { 8 + it.second.size }
                out.writeBytes("icns")            // magic, written verbatim (ASCII, big-endian semantics N/A)
                out.writeInt(totalSize)           // 4-byte big-endian total size (DataOutputStream is BE)

                for ((type, png) in chunks) {
                    out.writeBytes(type)          // 4-char type code
                    out.writeInt(8 + png.size)    // chunk size: type(4) + size(4) + payload
                    out.write(png)
                }
            }
            target.writeBytes(body.toByteArray())
        }
    }

    // ── DataOutputStream little-endian helpers ───────────────────────────────

    private fun DataOutputStream.writeLEShort(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
    }

    private fun DataOutputStream.writeLEInt(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
        write((v ushr 16) and 0xFF)
        write((v ushr 24) and 0xFF)
    }
}
