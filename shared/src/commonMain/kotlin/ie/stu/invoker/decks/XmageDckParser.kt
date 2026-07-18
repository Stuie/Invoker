package ie.stu.invoker.decks

/**
 * Parses XMage's native `.dck` format. Card lines look like:
 *
 * ```
 * 1 [2X2:117] Lightning Bolt
 * SB: 2 [MH2:5] Card Name
 * ```
 *
 * plus `NAME:` / `AUTHOR:` / `LAYOUT …` metadata and `#` comments, which are ignored. The card-line
 * regex is the one XMage's own `DckDeckImporter` uses. Both set code and collector number are always
 * present in this format, so every entry pins an exact printing.
 */
object XmageDckParser : DeckParser {

    // (SB:)? count [SET:collectorNumber] Card Name
    private val LINE = Regex("""^(SB:)?\s*(\d*)\s*\[([^\]:]+):([^\]:]+)]\s*(.*?)\s*$""")

    override fun canParse(raw: String): Boolean =
        raw.lineSequence().any { line ->
            val t = line.trim()
            t.startsWith("NAME:") || t.startsWith("AUTHOR:") || t.startsWith("LAYOUT") || LINE.matches(t)
        }

    override fun parse(raw: String): ParseResult {
        val entries = mutableListOf<DeckEntry>()
        val ignored = mutableListOf<String>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("NAME:") || line.startsWith("AUTHOR:") || line.startsWith("LAYOUT")) continue
            val m = LINE.matchEntire(line)
            if (m == null) {
                ignored += line
                continue
            }
            val sideboard = m.groupValues[1] == "SB:"
            val count = m.groupValues[2].toIntOrNull() ?: 1
            val set = m.groupValues[3].trim()
            val num = m.groupValues[4].trim()
            val name = m.groupValues[5].trim()
            if (name.isEmpty()) {
                ignored += line
                continue
            }
            entries += DeckEntry(
                count = count,
                name = name,
                setCode = set.uppercase(),
                collectorNumber = num,
                sideboard = sideboard,
            )
        }
        return ParseResult(entries, ignored)
    }
}
