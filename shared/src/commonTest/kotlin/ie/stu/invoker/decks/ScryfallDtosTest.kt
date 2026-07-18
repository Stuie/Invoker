package ie.stu.invoker.decks

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScryfallDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes collection response with a normal card, a DFC and a not_found`() {
        val payload = """
            {
              "object": "list",
              "not_found": [ { "set": "xxx", "collector_number": "999" } ],
              "data": [
                {
                  "object": "card",
                  "name": "Lightning Bolt",
                  "set": "2x2",
                  "collector_number": "117",
                  "image_uris": {
                    "small": "https://img/small.jpg",
                    "normal": "https://img/normal.jpg",
                    "large": "https://img/large.jpg",
                    "png": "https://img/large.png"
                  }
                },
                {
                  "object": "card",
                  "name": "Delver of Secrets // Insectile Aberration",
                  "set": "isd",
                  "collector_number": "51",
                  "card_faces": [
                    { "name": "Delver of Secrets", "image_uris": { "large": "https://img/front-large.jpg" } },
                    { "name": "Insectile Aberration" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ScryfallCollectionResponse>(payload)

        assertEquals(2, decoded.data.size)
        assertEquals(1, decoded.notFound.size)
        assertEquals("999", decoded.notFound.single().collectorNumber)

        val bolt = decoded.data[0]
        assertEquals("https://img/large.jpg", bolt.imageUrl(ImageQuality.Large))
        assertEquals("https://img/large.png", bolt.imageUrl(ImageQuality.Best))

        val dfc = decoded.data[1]
        assertNull(dfc.imageUris)
        // Falls back to the front face's image for double-faced cards.
        assertEquals("https://img/front-large.jpg", dfc.imageUrl(ImageQuality.Large))
    }

    @Test
    fun `image quality falls back to large when the requested size is absent`() {
        val uris = ScryfallImageUris(large = "https://img/large.jpg")
        assertEquals("https://img/large.jpg", uris.forKey(ImageQuality.Best))
        assertEquals("https://img/large.jpg", uris.forKey(ImageQuality.Small))
    }
}
