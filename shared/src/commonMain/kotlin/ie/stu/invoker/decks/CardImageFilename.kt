package ie.stu.invoker.decks

/**
 * Builds the on-disk path XMage's client expects for a card image, relative to
 * `mage-client/plugins/images/`. Mirrors XMage's `CardImageUtils.buildImagePathToCardOrToken` +
 * `prepareCardNameForFile` exactly so images we drop in are picked up without a re-download.
 *
 * Format: `<SETCODE>/<sanitised name>[.<collector number>].full.jpg`
 * - Set code is uppercased; the Windows-reserved `CON` becomes `COX` (XMage does the same).
 * - Card name: `//` (split cards) becomes `-`, then the Windows-illegal characters `\ / : * ? " < > |`
 *   are stripped.
 * - The collector-number segment is present only when the card name has multiple printings in the
 *   set (XMage's `usesVariousArt`); otherwise the bare name is used.
 */
object CardImageFilename {

    fun setFolder(setCode: String): String {
        val upper = setCode.uppercase()
        return if (upper == "CON") "COX" else upper
    }

    fun sanitizeCardName(name: String): String =
        name.replace("//", "-")
            .replace("\\", "")
            .replace("/", "")
            .replace(":", "")
            .replace("*", "")
            .replace("?", "")
            .replace("\"", "")
            .replace("<", "")
            .replace(">", "")
            .replace("|", "")

    /** Relative path using `/` separators, e.g. `2X2/Lightning Bolt.117.full.jpg`. */
    fun build(setCode: String, cardName: String, collectorNumber: String?, usesVariousArt: Boolean): String {
        val folder = setFolder(setCode)
        val name = sanitizeCardName(cardName)
        val suffix = if (usesVariousArt && !collectorNumber.isNullOrBlank()) ".$collectorNumber" else ""
        return "$folder/$name$suffix.full.jpg"
    }
}
