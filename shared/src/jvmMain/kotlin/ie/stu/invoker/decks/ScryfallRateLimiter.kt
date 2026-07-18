package ie.stu.invoker.decks

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Spaces out requests to `api.scryfall.com` so we stay under Scryfall's ~10 req/s guidance and never
 * trip the 429 that locks the app out for 30 seconds. Only the JSON API needs this — the image CDN
 * (`*.scryfall.io`) is explicitly not rate limited, so image downloads bypass it.
 *
 * A single instance is shared across every API call (resolution + print-count scans) so bursts can't
 * add up across concurrent work. Request *starts* are spaced by [minIntervalMs]; the actual request
 * may still overlap the next one's wait, which matches Scryfall's "requests per second" phrasing.
 */
class ScryfallRateLimiter(private val minIntervalMs: Long = 100) {
    private val mutex = Mutex()
    private var lastStartNanos = 0L

    suspend fun <T> withPermit(block: suspend () -> T): T {
        mutex.withLock {
            if (lastStartNanos != 0L) {
                val elapsedMs = (System.nanoTime() - lastStartNanos) / 1_000_000
                val wait = minIntervalMs - elapsedMs
                if (wait > 0) delay(wait.milliseconds)
            }
            lastStartNanos = System.nanoTime()
        }
        return block()
    }
}
