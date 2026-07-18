package ie.stu.invoker.decks

import kotlin.test.Test
import kotlin.test.assertEquals

class CardImageFilenameTest {

    @Test
    fun `set code is uppercased`() {
        assertEquals("2X2/Lightning Bolt.full.jpg", CardImageFilename.build("2x2", "Lightning Bolt", null, false))
    }

    @Test
    fun `reserved CON set folder becomes COX`() {
        assertEquals("COX", CardImageFilename.setFolder("con"))
        assertEquals("COX", CardImageFilename.setFolder("CON"))
    }

    @Test
    fun `split card double slash becomes dash`() {
        assertEquals("Fire - Ice", CardImageFilename.sanitizeCardName("Fire // Ice"))
        assertEquals("MH2/Fire - Ice.full.jpg", CardImageFilename.build("mh2", "Fire // Ice", null, false))
    }

    @Test
    fun `illegal characters are stripped`() {
        assertEquals(
            "Ratchet Bomb",
            CardImageFilename.sanitizeCardName("Rat/chet: Bomb*?\"<>|"),
        )
    }

    @Test
    fun `collector number included only when uses various art`() {
        assertEquals("ELD/Forest.full.jpg", CardImageFilename.build("ELD", "Forest", "266", false))
        assertEquals("ELD/Forest.266.full.jpg", CardImageFilename.build("ELD", "Forest", "266", true))
    }

    @Test
    fun `no collector number segment when number is blank even if various art`() {
        assertEquals("ELD/Forest.full.jpg", CardImageFilename.build("ELD", "Forest", null, true))
        assertEquals("ELD/Forest.full.jpg", CardImageFilename.build("ELD", "Forest", "", true))
    }
}
