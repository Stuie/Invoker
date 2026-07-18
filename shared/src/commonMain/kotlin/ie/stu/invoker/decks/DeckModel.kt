package ie.stu.invoker.decks

/**
 * One line of a parsed deck. A line carries a [count] (e.g. "4 Lightning Bolt"); the same physical
 * card image is fetched once regardless of count, so [count] is informational only.
 *
 * [setCode] / [collectorNumber] pin a specific printing when the source provided them (XMage `.dck`
 * always does; generic exports sometimes do). Both null means "any printing of this name".
 */
data class DeckEntry(
    val count: Int,
    val name: String,
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val sideboard: Boolean = false,
)

enum class DeckFormat { XmageDck, GenericText }

/** Result of parsing raw deck text: the recognised [entries] plus any lines we couldn't make sense of. */
data class ParsedDeck(
    val entries: List<DeckEntry>,
    val format: DeckFormat,
    val ignoredLines: List<String> = emptyList(),
)

/**
 * Image resolution to request from Scryfall. XMage stores every card as `<name>.full.jpg` and only
 * checks for file existence (not dimensions), so whichever size we fetch first is what the user sees
 * until they delete it. [Best] downloads Scryfall's PNG (highest quality, rounded corners) but still
 * writes it to the `.full.jpg` path — XMage reads image bytes by content, not by extension.
 */
enum class ImageQuality(val scryfallKey: String) {
    Small("small"),
    Normal("normal"),
    Large("large"),
    Best("png"),
}
