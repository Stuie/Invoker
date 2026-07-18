package ie.stu.invoker.decks

import ie.stu.invoker.BuildInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Outcome of importing a deck from a URL. */
sealed interface DeckImportResult {
    data class Success(val entries: List<DeckEntry>, val name: String?) : DeckImportResult
    /** Host we deliberately don't call (e.g. Moxfield) — the user should paste the text export instead. */
    data class Unsupported(val host: String) : DeckImportResult
    data class Failed(val message: String) : DeckImportResult
}

/**
 * Imports decks from deck-site URLs. Only **Archidekt** is fetched — it exposes an open public JSON
 * API. Moxfield and any other host return [DeckImportResult.Unsupported] **without any network
 * request**: Moxfield gates its API and asks third parties not to use it, so we route users to its
 * "Export" button + paste instead.
 */
class DeckUrlImporter(private val client: HttpClient = defaultClient()) {

    suspend fun fetch(url: String): DeckImportResult {
        val trimmed = url.trim()
        val archidektId = ARCHIDEKT_ID.find(trimmed)?.groupValues?.get(1)
        return when {
            archidektId != null -> fetchArchidekt(archidektId)
            trimmed.contains("moxfield.", ignoreCase = true) -> DeckImportResult.Unsupported("Moxfield")
            else -> DeckImportResult.Unsupported(hostOf(trimmed))
        }
    }

    private suspend fun fetchArchidekt(id: String): DeckImportResult {
        return try {
            val deck: ArchidektDeck = client.get("https://archidekt.com/api/decks/$id/").body()
            val entries = deck.cards.mapNotNull { c ->
                val cats = c.categories.map { it.lowercase() }
                // Maybeboard cards aren't part of the deck.
                if ("maybeboard" in cats) return@mapNotNull null
                val name = c.card.oracleCard.name.trim()
                if (name.isEmpty()) return@mapNotNull null
                DeckEntry(
                    count = c.quantity.coerceAtLeast(1),
                    name = name,
                    setCode = c.card.edition.editionCode.ifBlank { null }?.uppercase(),
                    collectorNumber = c.card.collectorNumber.ifBlank { null },
                    sideboard = "sideboard" in cats,
                )
            }
            if (entries.isEmpty()) DeckImportResult.Failed("No cards found in that Archidekt deck.")
            else DeckImportResult.Success(entries, deck.name.ifBlank { null })
        } catch (e: Exception) {
            DeckImportResult.Failed(e.message ?: "Couldn't reach Archidekt.")
        }
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

    companion object {
        private val ARCHIDEKT_ID = Regex("""archidekt\.com/(?:api/)?decks/(\d+)""", RegexOption.IGNORE_CASE)

        fun defaultClient(): HttpClient = HttpClient {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(DefaultRequest) {
                header(HttpHeaders.UserAgent, "Invoker/${BuildInfo.LAUNCHER_VERSION}")
                header(HttpHeaders.Accept, "application/json;q=0.9,*/*;q=0.8")
            }
        }
    }
}
