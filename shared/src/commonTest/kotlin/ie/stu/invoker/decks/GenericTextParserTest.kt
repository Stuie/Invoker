package ie.stu.invoker.decks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenericTextParserTest {

    @Test
    fun `plain count and name`() {
        val e = GenericTextParser.parse("4 Lightning Bolt").entries.single()
        assertEquals(4, e.count)
        assertEquals("Lightning Bolt", e.name)
        assertNull(e.setCode)
        assertNull(e.collectorNumber)
    }

    @Test
    fun `count with x suffix`() {
        val e = GenericTextParser.parse("1x Sol Ring").entries.single()
        assertEquals(1, e.count)
        assertEquals("Sol Ring", e.name)
    }

    @Test
    fun `name only defaults to count one`() {
        val e = GenericTextParser.parse("Sol Ring").entries.single()
        assertEquals(1, e.count)
        assertEquals("Sol Ring", e.name)
    }

    @Test
    fun `set and collector number in parens`() {
        val e = GenericTextParser.parse("4 Lightning Bolt (2X2) 117").entries.single()
        assertEquals(4, e.count)
        assertEquals("Lightning Bolt", e.name)
        assertEquals("2X2", e.setCode)
        assertEquals("117", e.collectorNumber)
    }

    @Test
    fun `trailing foil flags are ignored`() {
        val e = GenericTextParser.parse("2 Fabled Passage (ELD) 244 *F*").entries.single()
        assertEquals("Fabled Passage", e.name)
        assertEquals("ELD", e.setCode)
        assertEquals("244", e.collectorNumber)
    }

    @Test
    fun `sideboard header flips subsequent cards`() {
        val deck = """
            Deck
            4 Lightning Bolt (2X2) 117

            Sideboard
            2 Negate
        """.trimIndent()
        val entries = GenericTextParser.parse(deck).entries
        assertEquals(2, entries.size)
        assertTrue(!entries[0].sideboard)
        assertTrue(entries[1].sideboard)
        assertEquals("Negate", entries[1].name)
    }

    @Test
    fun `SB prefix marks sideboard`() {
        val e = GenericTextParser.parse("SB: 2 Negate").entries.single()
        assertEquals(2, e.count)
        assertEquals("Negate", e.name)
        assertTrue(e.sideboard)
    }

    @Test
    fun `comments and blanks are skipped`() {
        val deck = """
            # my deck
            // another comment

            1 Sol Ring
        """.trimIndent()
        val r = GenericTextParser.parse(deck)
        assertEquals(1, r.entries.size)
        assertEquals("Sol Ring", r.entries.single().name)
    }

    @Test
    fun `commander header does not become a card and following card is main`() {
        val deck = """
            Commander
            1 Kenrith, the Returned King (ELD) 303

            Deck
            1 Sol Ring (C21) 263
        """.trimIndent()
        val entries = GenericTextParser.parse(deck).entries
        assertEquals(2, entries.size)
        assertEquals("Kenrith, the Returned King", entries[0].name)
        assertTrue(!entries[0].sideboard)
        assertEquals("Sol Ring", entries[1].name)
    }
}
