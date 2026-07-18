package ie.stu.invoker.decks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmageDckParserTest {

    @Test
    fun `detects dck by bracket lines and headers`() {
        assertTrue(XmageDckParser.canParse("1 [2X2:117] Lightning Bolt"))
        assertTrue(XmageDckParser.canParse("NAME:My Deck"))
        assertTrue(XmageDckParser.canParse("LAYOUT MAIN:(1,1)|(([2X2:117]))"))
        assertTrue(!XmageDckParser.canParse("4 Lightning Bolt"))
    }

    @Test
    fun `parses card lines with set and collector number`() {
        val r = XmageDckParser.parse("1 [2X2:117] Lightning Bolt")
        assertEquals(1, r.entries.size)
        val e = r.entries.first()
        assertEquals(1, e.count)
        assertEquals("Lightning Bolt", e.name)
        assertEquals("2X2", e.setCode)
        assertEquals("117", e.collectorNumber)
        assertTrue(!e.sideboard)
    }

    @Test
    fun `sideboard prefix is recognised`() {
        val r = XmageDckParser.parse("SB: 2 [MH2:5] Counterspell")
        val e = r.entries.single()
        assertEquals(2, e.count)
        assertEquals("Counterspell", e.name)
        assertEquals("MH2", e.setCode)
        assertTrue(e.sideboard)
    }

    @Test
    fun `metadata comments and layout lines are ignored`() {
        val deck = """
            NAME:Test
            AUTHOR:Me
            # a comment
            1 [ELD:266] Forest
            LAYOUT MAIN:(1,1)|(([ELD:266]))
        """.trimIndent()
        val r = XmageDckParser.parse(deck)
        assertEquals(1, r.entries.size)
        assertEquals("Forest", r.entries.single().name)
        assertTrue(r.ignoredLines.isEmpty())
    }

    @Test
    fun `hyphenated set codes are handled`() {
        val e = XmageDckParser.parse("1 [MPS-AKH:49] Cancel").entries.single()
        assertEquals("MPS-AKH", e.setCode)
        assertEquals("49", e.collectorNumber)
    }
}
