package ie.stu.invoker.decks

import ie.stu.invoker.BuildInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin client for the parts of the Scryfall REST API we need, wrapped so we stay a good citizen:
 * - every request goes through a shared [ScryfallRateLimiter] (≤10 req/s), so bursts across a whole
 *   deck — or several decks in a row — can never trip the 429 that locks the app out for 30 seconds;
 * - a descriptive `User-Agent` + `Accept` header on every request;
 * - automatic backoff/retry on 429 and 5xx, honouring `Retry-After`.
 *
 * See https://scryfall.com/docs/api/rate-limits.
 */
class ScryfallService(
    private val rateLimiter: ScryfallRateLimiter = ScryfallRateLimiter(),
    private val client: HttpClient = defaultClient(),
) {
    // Cache of set code (lowercase) -> map of card name (lowercase) -> number of printings in that set.
    private val setPrintCounts = ConcurrentHashMap<String, Map<String, Int>>()

    /**
     * Resolves card identifiers via `POST /cards/collection` (75 per request). Returns the union of
     * every batch's `data` and `not_found`.
     */
    suspend fun resolveCollection(identifiers: List<ScryfallIdentifier>): ScryfallCollectionResponse {
        if (identifiers.isEmpty()) return ScryfallCollectionResponse()
        val found = mutableListOf<ScryfallCard>()
        val notFound = mutableListOf<ScryfallIdentifier>()
        for (batch in identifiers.chunked(75)) {
            val response: ScryfallCollectionResponse = rateLimiter.withPermit {
                client.post("$BASE/cards/collection") {
                    contentType(ContentType.Application.Json)
                    setBody(ScryfallCollectionRequest(batch))
                }.body()
            }
            found += response.data
            notFound += response.notFound
        }
        return ScryfallCollectionResponse(found, notFound)
    }

    /**
     * Warms the printing-count cache for [set] with a single paginated search, so the per-card
     * [usesVariousArt] lookups that follow cost no further API calls. Safe to call repeatedly and
     * concurrently; a lookup failure caches an empty map (treated as "single printing").
     */
    suspend fun warmSet(set: String) {
        val key = set.lowercase()
        if (setPrintCounts.containsKey(key)) return
        val counts = runCatching { fetchSetPrintCounts(key) }.getOrDefault(emptyMap())
        setPrintCounts.putIfAbsent(key, counts)
    }

    /**
     * Whether XMage puts the collector number in the image filename for this card — true when the
     * card name has 2+ printings in [set] (XMage's `usesVariousArt`). Reads the cache warmed by
     * [warmSet]; falls back to a direct warm if it wasn't pre-fetched.
     */
    suspend fun usesVariousArt(name: String, set: String): Boolean {
        warmSet(set)
        val counts = setPrintCounts[set.lowercase()] ?: return false
        return (counts[name.lowercase()] ?: 1) >= 2
    }

    /** The complete list of mana/symbol SVG URLs from `/symbology` (one API call). */
    suspend fun symbolSvgUrls(): List<String> {
        val page: ScryfallSymbolList = rateLimiter.withPermit { client.get("$BASE/symbology").body() }
        return page.data.mapNotNull { it.svgUri.ifBlank { null } }
    }

    private suspend fun fetchSetPrintCounts(setLower: String): Map<String, Int> {
        val counts = HashMap<String, Int>()
        var url: String? = "$BASE/cards/search"
        var page = 0
        while (url != null && page < MAX_SET_PAGES) {
            val requestUrl = url
            val isFirst = page == 0
            val response: ScryfallSearchPage = rateLimiter.withPermit {
                client.get(requestUrl) {
                    if (isFirst) {
                        parameter("q", "set:$setLower")
                        parameter("unique", "prints")
                    }
                }.body()
            }
            response.data.forEach { card ->
                val n = card.name.lowercase()
                counts[n] = (counts[n] ?: 0) + 1
            }
            url = if (response.hasMore) response.nextPage else null
            page++
        }
        return counts
    }

    companion object {
        private const val BASE = "https://api.scryfall.com"
        private const val MAX_SET_PAGES = 6 // 175 cards/page — covers even the largest sets.

        fun defaultClient(): HttpClient = HttpClient {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(HttpRequestRetry) {
                // Safety net only — the rate limiter should keep us from ever seeing a 429. Honours
                // Retry-After via exponentialDelay's default.
                maxRetries = 3
                retryIf { _, response -> response.status.value == 429 || response.status.value >= 500 }
                retryOnExceptionIf(maxRetries = 2) { _, cause -> cause is java.io.IOException }
                exponentialDelay(base = 2.0, maxDelayMs = 20_000)
            }
            install(DefaultRequest) {
                header(HttpHeaders.UserAgent, "Invoker/${BuildInfo.LAUNCHER_VERSION}")
                header(HttpHeaders.Accept, "application/json;q=0.9,*/*;q=0.8")
            }
        }
    }
}
