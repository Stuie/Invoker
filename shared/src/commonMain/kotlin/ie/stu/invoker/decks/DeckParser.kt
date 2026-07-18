package ie.stu.invoker.decks

/** Recognised entries plus lines a parser couldn't interpret (surfaced to the user, not discarded silently). */
data class ParseResult(val entries: List<DeckEntry>, val ignoredLines: List<String> = emptyList())

interface DeckParser {
    /** Whether this parser recognises the input's shape. */
    fun canParse(raw: String): Boolean
    fun parse(raw: String): ParseResult
}

/**
 * Auto-detecting entry point. The XMage `.dck` parser is tried first (its bracketed `[SET:num]` form
 * and `NAME:`/`LAYOUT` headers are unambiguous); everything else — Moxfield / MTGA / Archidekt text
 * exports and plain lists — falls to the generic text parser.
 */
object DeckParsers {
    fun parse(raw: String): ParsedDeck {
        return if (XmageDckParser.canParse(raw)) {
            val r = XmageDckParser.parse(raw)
            ParsedDeck(r.entries, DeckFormat.XmageDck, r.ignoredLines)
        } else {
            val r = GenericTextParser.parse(raw)
            ParsedDeck(r.entries, DeckFormat.GenericText, r.ignoredLines)
        }
    }
}
