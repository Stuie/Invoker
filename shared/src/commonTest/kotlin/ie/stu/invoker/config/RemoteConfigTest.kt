package ie.stu.invoker.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemoteConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes live xmage_today config shape verbatim`() {
        // Lifted from XMageLauncher/config_example/config.json.
        val payload = """
            {
                "java": {
                    "version": "1.8.0_201",
                    "location": "https://xmage.today/java/jre-8u201-"
                },
                "XMage": {
                    "version": "1.4.55-dev (2024-12-01 17-53)",
                    "location": "https://beta.xmage.today/files/mage-update_1.4.55-dev_2024-12-01_17-53.zip",
                    "locations": [],
                    "full": "https://beta.xmage.today/files/mage-full_1.4.55-dev_2024-12-01_17-53.zip",
                    "torrent": "",
                    "images": "",
                    "Launcher": {
                        "version": "0.3.8",
                        "location": "https://beta.xmage.today/files/XMageLauncher-0.3.8.jar"
                    }
                }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<RemoteConfig>(payload)
        assertEquals("1.8.0_201", decoded.java.version)
        assertEquals("1.4.55-dev (2024-12-01 17-53)", decoded.xmage.version)
        assertTrue(decoded.xmage.locations.isEmpty())
        assertEquals("0.3.8", decoded.xmage.launcher.version)
    }

    @Test
    fun `unknown fields at any level are ignored`() {
        val payload = """
            {
                "java": { "version": "1.8.0_201", "location": "x", "unknown_field": "ignored" },
                "XMage": {
                    "version": "1.4.55", "location": "x", "full": "x",
                    "Launcher": { "version": "0.1.0", "location": "x" },
                    "future_field": 42
                },
                "top_level_extra": true
            }
        """.trimIndent()
        val decoded = json.decodeFromString<RemoteConfig>(payload)
        assertNotNull(decoded)
        assertEquals("1.4.55", decoded.xmage.version)
    }

    @Test
    fun `optional fields fall back to defaults when missing`() {
        // No locations, torrent, images.
        val payload = """
            {
                "java": { "version": "1.8.0_201", "location": "x" },
                "XMage": {
                    "version": "1.4.55",
                    "location": "x",
                    "Launcher": { "version": "0.1.0", "location": "x" }
                }
            }
        """.trimIndent()
        val decoded = json.decodeFromString<RemoteConfig>(payload)
        assertTrue(decoded.xmage.locations.isEmpty())
        assertEquals("", decoded.xmage.full)
        assertEquals("", decoded.xmage.torrent)
        assertEquals("", decoded.xmage.images)
    }

    @Test
    fun `mirror list deserialises in order`() {
        val payload = """
            {
                "java": { "version": "1", "location": "x" },
                "XMage": {
                    "version": "1.4.55", "location": "primary.example",
                    "locations": ["mirror-a.example", "mirror-b.example"],
                    "Launcher": { "version": "0.1.0", "location": "x" }
                }
            }
        """.trimIndent()
        val decoded = json.decodeFromString<RemoteConfig>(payload)
        assertEquals(listOf("mirror-a.example", "mirror-b.example"), decoded.xmage.locations)
    }
}
