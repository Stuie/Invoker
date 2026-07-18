package ie.stu.invoker.decks

/**
 * Parses the plain-text deck exports produced by Moxfield, MTG Arena, Archidekt and friends, plus
 * bare card lists. Handled shapes (count optional, defaults to 1):
 *
 * ```
 * 4 Lightning Bolt
 * 1x Sol Ring
 * 4 Lightning Bolt (2X2) 117
 * 2 Fabled Passage (ELD) 244 *F*      # trailing foil/flags ignored
 * SB: 2 Negate
 * ```
 *
 * Section headers (`Deck`, `Sideboard`, `Commander`, `Companion`, …) are recognised on their own
 * line; only `Sideboard` flips subsequent cards into the sideboard.
 */
object GenericTextParser : DeckParser {

    private val LINE = Regex(
        "^(?:(SB):\\s*)?" +                             // optional sideboard marker
            "(?:(\\d+)\\s*[xX]?\\s+)?" +                // optional count (+ optional 'x')
            "(.+?)" +                                    // card name (lazy)
            "(?:\\s+\\(([A-Za-z0-9]{2,6})\\)" +          // optional (SET)
            "(?:\\s+([0-9A-Za-z★✳+\\-]+))?)?" + // optional collector number
            "(?:\\s+\\*[^*]*\\*)*" +                     // optional trailing *F* / *E* flags
            "\\s*$",
    )

    private val SECTIONS = setOf(
        "deck", "sideboard", "commander", "commanders", "companion", "maybeboard", "tokens", "considering",
    )

    override fun canParse(raw: String): Boolean = true // fallback parser

    override fun parse(raw: String): ParseResult {
        val entries = mutableListOf<DeckEntry>()
        val ignored = mutableListOf<String>()
        var sideboardMode = false
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            val header = line.removeSuffix(":").trim().lowercase()
            if (header in SECTIONS) {
                sideboardMode = header == "sideboard"
                continue
            }

            val m = LINE.matchEntire(line)
            if (m == null) {
                ignored += line
                continue
            }
            val sideboard = sideboardMode || m.groupValues[1] == "SB"
            val count = m.groupValues[2].toIntOrNull() ?: 1
            val name = m.groupValues[3].trim()
            val set = m.groupValues[4].trim().ifEmpty { null }?.uppercase()
            val num = m.groupValues[5].trim().ifEmpty { null }
            if (name.isEmpty()) {
                ignored += line
                continue
            }
            entries += DeckEntry(
                count = count,
                name = name,
                setCode = set,
                collectorNumber = if (set != null) num else null,
                sideboard = sideboard,
            )
        }
        return ParseResult(entries, ignored)
    }
}
